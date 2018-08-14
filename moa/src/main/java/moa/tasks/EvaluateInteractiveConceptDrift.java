/*
 *    EvaluatePrequential.java
 *    Copyright (C) 2007 University of Waikato, Hamilton, New Zealand
 *    @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
 *    @author Albert Bifet (abifet at cs dot waikato dot ac dot nz)
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program. If not, see <http://www.gnu.org/licenses/>.
 *    
 */
package moa.tasks;

import com.github.javacliparser.FileOption;
import com.github.javacliparser.FlagOption;
import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.github.javacliparser.MultiChoiceOption;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import moa.classifiers.Classifier;
import moa.classifiers.interactive.ConfidenceMeasure;
import moa.classifiers.interactive.EMIC;
import moa.core.Example;
import moa.core.InstanceExample;
import moa.core.Measurement;
import moa.core.ObjectRepository;
import moa.core.TimingUtils;
import moa.core.Utils;
import moa.evaluation.LearningCurve;
import moa.evaluation.LearningEvaluation;
import moa.evaluation.LearningPerformanceEvaluator;
import moa.learners.ChangeDetectorLearner;
import moa.options.ClassOption;
import moa.streams.ExampleStream;
import moa.streams.InstanceStream;

import moa.streams.clustering.ClusterEvent;
import moa.streams.generators.cd.ArffFileGenerator;
import moa.streams.generators.cd.ConceptDriftGenerator;
import moa.streams.generators.cd.ExampleDriftGenerator;
import moa.streams.generators.cd.InstanceDriftGenerator;
import static moa.tasks.MainTask.INSTANCES_BETWEEN_MONITOR_UPDATES;


/**
 * Task for evaluating a classifier on a stream by testing then training with each example in sequence.
 *
 * @author Richard Kirkby (rkirkby@cs.waikato.ac.nz)
 * @author Albert Bifet (abifet at cs dot waikato dot ac dot nz)
 * @version $Revision: 7 $
 */
public class EvaluateInteractiveConceptDrift extends ConceptDriftMainTask{

   
    @Override
    public String getPurposeString() {
        return "Evaluates a classifier on a stream by testing then training with each example in sequence.";
    }
    
    // Private class to store the different confidence values with their results of classification
	private class Prediction implements Comparable{
		
		public int numInstance;
		
		public double confidenceValue;
		
		public boolean loss;  // 1 = correct, 0 = incorrect

		@Override
		public int compareTo(Object arg0) {
			Prediction p = (Prediction) arg0;
			if (this.confidenceValue == p.confidenceValue && this.numInstance==p.numInstance){
				return 0;
			}
			// No es suficiente con restarlos, porque al hacer el casting a entero 
			// puede pasar que -0.01 se convierta en 0 y diga que son iguales.
			return this.confidenceValue < p.confidenceValue ? 1 : -1;
		}
		
	}
        
        private class WarningPrediction {
            public int trueClassInteractive;
            public double[] copyPrediction;
            public long instancesProcessed;
        }

    private static final long serialVersionUID = 1L;

    public ClassOption learnerOption = new ClassOption("learner", 'l',
            "Change detector to train.", ChangeDetectorLearner.class, "ChangeDetectorLearner");

    public ClassOption streamOption = new ClassOption("stream", 's',
            "Stream to learn from.", ExampleStream.class,
            "generators.RandomTreeGenerator");

    public ClassOption evaluatorOption = new ClassOption("evaluator", 'e',
            "Classification performance evaluation method.",
            LearningPerformanceEvaluator.class,
            "BasicConceptDriftPerformanceEvaluator");

    public IntOption instanceLimitOption = new IntOption("instanceLimit", 'i',
            "Maximum number of instances to test/train on  (-1 = no limit).",
            1000, -1, Integer.MAX_VALUE);

    public IntOption timeLimitOption = new IntOption("timeLimit", 't',
            "Maximum number of seconds to test/train for (-1 = no limit).", -1,
            -1, Integer.MAX_VALUE);

    public IntOption sampleFrequencyOption = new IntOption("sampleFrequency",
            'f',
            "How many instances between samples of the learning performance.",
            10, 0, Integer.MAX_VALUE);

    /*public IntOption memCheckFrequencyOption = new IntOption(
            "memCheckFrequency", 'q',
            "How many instances between memory bound checks.", 100000, 0,
            Integer.MAX_VALUE);*/

    public FileOption dumpFileOption = new FileOption("dumpFile", 'd',
            "File to append intermediate csv results to.", null, "csv", true);

    /*public FileOption outputPredictionFileOption = new FileOption("outputPredictionFile", 'o',
            "File to append output predictions to.", null, "pred", true);*/
    //Interactive option using for confidence measure
    public ClassOption confidenceMeasureOption = new ClassOption("confidenceMeasure", 'c',
            "Confidence measure to get the confidence degree for asking the oracle.",
            ConfidenceMeasure.class, "MaximumProbability");
    
    //Interactive option using for emic measure
    public ClassOption emicMeasureOption = new ClassOption("emicMeasure", 'h',
            "EMIC measure", EMIC.class, "Fbeta");
    
    //Interactive option using for asking method to the oracle
    public MultiChoiceOption askingMethodOption = new MultiChoiceOption(
            "askingMethod", 'u', "Method for asking to the oracle.", new String[]{
                "Threshold", "Probability", "NoAsk"}, new String[]{
                "Threshold", "Probability", "NoAsk"}, 0);
    
    //Interactive option using for probability asking method
    public MultiChoiceOption probAskingMethodOption = new MultiChoiceOption(
            "probAskingMethod", 'j', "Method using random values for asking for the label.", new String[]{
                "Complementary", "Moderate", "WeightProb", "ThresProb"}, new String[]{
                "Complementary", "Moderate", "WeightProb", "ThresProb"}, 0);

    //Interactive option using to set the number of instances before start evaluation
    public IntOption sizeTrainingSetOption = new IntOption("sizeTrainingSet", 'm',
            "Number of instances to train before start evaluation.",
            0, 0, Integer.MAX_VALUE);
    
    public FileOption outputPredictionFileOption = new FileOption("outputPredictionFile", 'o',
            "File to append output predictions to.", null, "pred", true);

    public FileOption outputProbabilityFileOption = new FileOption("outputProbabilityFile", 'p',
            "File to append output probability (and predicted and true class) to.", null, "prob", true);
    
    //Interactive option using in never learn situation
    public FlagOption neverLearnOption = new FlagOption("neverLearn", 'n', "Never learn new instances during testing");
    
    //Interactive option using for dynamic threshold calculation
    public FlagOption dynamicThresholdOption = new FlagOption("dynamicThreshold", 'y', "Calculate confidence threshold dynamically");
    
    //Interactive option using for change threshold if there is better emic
    public FlagOption changeThresholdIfBetterEmicOption = new FlagOption("changeThresholdIfBetterEmic", 'b', "Only change the threshold if the EMIC is improved");
    
    //Interactive option to summarize training set
    public FlagOption summarizeTrainingSetOption = new FlagOption("summarizeTrainingSet", 'z', "Summarize the training set to get more performance.");

    //Interactive option to jump sample
    public FlagOption jumpOption = new FlagOption("JumpSample15000", 'x', "Jump to sample 15000 from training sample");

    //Interactive option using for the weight of instances labelled from the oracle
    public FloatOption weightOracleOption = new FloatOption("weightOracle", 'k', "Weight for instances labelled from the oracle", 1, 0, Double.MAX_VALUE);

    public IntOption randomSeedOption = new IntOption("randomSeed", 'r',
            "Seed for random values.",
            1, 0, Integer.MAX_VALUE);
    
    //For quality assurance, question to oracle
    //the emic measure changes according that
    public IntOption qoaFrequencyOption = new IntOption("qoa",
        'v', "Number of questions to the oracle to ensure quality", 0);
    
    //To pass information of warning + drift to the oracle to recalculate its measures, emic, threshold
    //etc.
    public IntOption driftingOption = new IntOption("driftingOracle",
        'g', "Number of instances to update the oracle measures when drifting occurs", 0);
    
    public FileOption warningDriftFileOption = new FileOption("warningDriftFile", 'ñ',
            "File to append emic and threshold measures of warning and drift instances.", null, "ward", true);
    
    
    //private ArrayList<Prediction> globalPredictions = new ArrayList<Prediction>();
    private SortedSet<Prediction> globalPredictions = new TreeSet<Prediction>();
    
    private ArrayList<WarningPrediction> warningPredictions = new ArrayList<WarningPrediction>();
    
    List<Example> trainExamplesWarning = new ArrayList<Example>();
    List<Example> trainExamples = new ArrayList<Example>();
    Set<Example> trainExamplesSets = new HashSet<Example>();
    
    
    private double previousBestEmicValue = 0.0;
    
    private int sizeSummaryData = 0;
    private int successesSummaryData = 0;
    private int failsSummaryData = 0;
    
    
    @Override
    public Class<?> getTaskResultType() {
        return LearningCurve.class;
    }

    
    @Override
    protected Object doMainTask(TaskMonitor monitor, ObjectRepository repository) {
        Random random = new Random(randomSeedOption.getValue()); // el parametro r se queda aqui SE TRATA DE OTRA SECUENCIA!!!!
        
        ChangeDetectorLearner learner = (ChangeDetectorLearner) getPreparedClassOption(this.learnerOption);
        ConceptDriftGenerator stream = (ConceptDriftGenerator) getPreparedClassOption(this.streamOption);
        this.setEventsList(stream.getEventsList());
        LearningPerformanceEvaluator evaluator = (LearningPerformanceEvaluator) getPreparedClassOption(this.evaluatorOption);
        LearningCurve learningCurve = new LearningCurve(
                "learning evaluation instances");

        learner.setModelContext(stream.getHeader());
        
        //Using for Interactive
        EMIC emicMeasure = (EMIC) getPreparedClassOption(this.emicMeasureOption);
        EMIC temporalEmicMeasure = (EMIC) emicMeasure.copy();
        
        //Using for Interactive
        ConfidenceMeasure confidenceMeasure = (ConfidenceMeasure) getPreparedClassOption(this.confidenceMeasureOption);
        
        //Using for Interactive
        int sizeTrainingSet = sizeTrainingSetOption.getValue();
        
        // ============== Training ==============
        // Guardamos las instancias que se han usado para entrenar, que nos servirán para calcular el umbral
        Instances trainInstances = new Instances(stream.getHeader(), sizeTrainingSet);
                
     
        long instancesTrained = 0;
        int maxInstances = this.instanceLimitOption.getValue();
        long instancesProcessed = 0;
        long tempInstancesProcessed = 0;
        int maxSeconds = this.timeLimitOption.getValue();
        int secondsElapsed = 0;
        monitor.setCurrentActivity("Evaluating learner...", -1.0);

        while (stream.hasMoreInstances() && instancesTrained < sizeTrainingSet) {
        	Example trainInst = stream.nextInstance();
                InstanceExample trainInstExample = (InstanceExample)trainInst;
                Instance ins1 = trainInstExample.instance;
                Instance ins = ins1;
        	trainInstances.add(ins);
        	learner.trainOnInstance(trainInst);
        	instancesTrained++;
        }
        int askingMethod = askingMethodOption.getChosenIndex();
        int probAskingMethod = probAskingMethodOption.getChosenIndex();
        double weightProb=1.0;
        double confidenceThreshold=0;
        
        confidenceThreshold = calculateThreshold((Classifier)learner, emicMeasure, confidenceMeasure, instancesTrained, trainInstances, 1.0);
        
        if (probAskingMethod > 1) {// weightProb or ThresProb
        // ============== Cálculo de weight/ confidence usando las instancias de training ==============
           // weightProb =  calculateWeightProb((Classifier)learner, emicMeasure,
		//		confidenceMeasure, random, instancesTrained, trainInstances, 10, 20);
            System.out.println("@@@ weightProb"+weightProb);

        }
        if(summarizeTrainingSetOption.isSet()){
            summarizeTrainingSet(confidenceThreshold);
        }
        
        //Using for Interactive
        int successes = 0;
        //Using for Interactive
        int fails = 0;
        //Using for Interactive
        int interactions = 0;
        
        File dumpFile = this.dumpFileOption.getFile();
        PrintStream immediateResultStream = null;
        if (dumpFile != null) {
            try {
                if (dumpFile.exists()) {
                    immediateResultStream = new PrintStream(
                            new FileOutputStream(dumpFile, true), true);
                } else {
                    immediateResultStream = new PrintStream(
                            new FileOutputStream(dumpFile), true);
                }
            } catch (Exception ex) {
                throw new RuntimeException(
                        "Unable to open immediate result file: " + dumpFile, ex);
            }
        }
        //File for output predictions
        File outputPredictionFile = this.outputPredictionFileOption.getFile();
        PrintStream outputPredictionResultStream = null;
        if (outputPredictionFile != null) {
            try {
                if (outputPredictionFile.exists()) {
                    outputPredictionResultStream = new PrintStream(
                            new FileOutputStream(outputPredictionFile, true), true);
                } else {
                    outputPredictionResultStream = new PrintStream(
                            new FileOutputStream(outputPredictionFile), true);
                }
            } catch (Exception ex) {
                throw new RuntimeException(
                        "Unable to open prediction result file: " + outputPredictionFile, ex);
            }
        }
        boolean firstDump = true;
        boolean preciseCPUTiming = TimingUtils.enablePreciseTiming();
        long evaluateStartTime = TimingUtils.getNanoCPUTimeOfCurrentThread();
        long lastEvaluateStartTime = evaluateStartTime;
        double RAMHours = 0.0;
        
        /* Se ha añadido para la realizacion de la experimentacion */
        boolean salto = this.jumpOption.isSet();
        if(salto){
        instancesProcessed= 0;
        while (stream.hasMoreInstances() && (instancesProcessed + instancesTrained) < 15000) {
            Example trainInst = stream.nextInstance();
        	instancesProcessed++;
        }
        instancesProcessed= 0;
        }
        
        if (outputPredictionFile != null) { // Output prediction
            outputPredictionResultStream.println(emicMeasure.getValue()+ "!!!!!!!!!!!!!!!"+confidenceThreshold);
            if (probAskingMethod > 1)
                outputPredictionResultStream.println("@@@ weightProb"+weightProb);

        }
        
        Double driftValueToCompare = new Double("0.0");
        int controllerCountDrift = 0;
        boolean ifDrift = false;
        boolean siCambio = false;
        double tempEmic = 0;
        //Validar especificación de parámetro en la línea de comando para evitar error de
        //instanciación de variables
        //por ejemplo 100 el valor del parámetro driftingOption + lo acumulado del warning
        Instances trainInstancesWarning = new Instances(stream.getHeader(), maxInstances);  
        while (stream.hasMoreInstances()
                && ((maxInstances < 0) || (instancesProcessed < maxInstances))
                && ((maxSeconds < 0) || (secondsElapsed < maxSeconds))) {
            Example trainInst = (Example) stream.nextInstance();
            Example testInst = trainInst; 
            //Using for Interactive
            int trueClassInteractive = (int) ((Instance) trainInst.getData()).classValue();
            //Using for CD
            int trueClass = (int) ((Instance)trainInst.getData()).classValue();
            //double valorAtributo = ((Instance)trainInst.getData()).value(1);
            //System.out.println("valor atributo " + valorAtributo);
            //testInst.setClassMissing();
            double[] prediction = learner.getVotesForInstance(testInst);
            
            System.out.println("prediccion 0 " + prediction[0]);
            System.out.println("prediccion 1 " + prediction[1]);
            double[] predictionOracle = prediction;
            if (prediction[0] ==1 ){ //Change detected
                this.getEventsList().add(new ClusterEvent(this, instancesProcessed, "Detected Change", "Drift"));
            }
            
            //Using for Interactive
            try{
            	moa.core.Utils.normalize(prediction); //normalizamos para que la suma de valores sea 1
            } catch (Exception e) {
            
                String probString = "[";

                for(double pTmp : prediction){
                    probString += pTmp + " ";
                }
                probString += "]";

                // el perceptron nos puede devolver todas las probabilidades a 0
                for (int i = 0; i < prediction.length; i++) 
                    prediction[i] = 1.0/((Instance)trainInst.getData()).numClasses();
            }
            
            double[] copyPrediction = prediction.clone();
        	
            boolean askOracle = false;
            int predictedClass = moa.core.Utils.maxIndex(prediction);
            double confidenceDegree;
            
            System.out.println("prediction normalizado " + prediction[0]);
            System.out.println("trueClassInteractive: " + trueClassInteractive + " predictedClass " + predictedClass);
            System.out.println("instancias al momento " + instancesProcessed);
            
            switch (askingMethod) {
		case 0: //Threshold
		// Preguntamos si la medida de confianza no supera el umbral establecido
                    confidenceDegree = confidenceMeasure.getValue(prediction);
                    askOracle = confidenceDegree < confidenceThreshold; // !!! CAMBIO
                    break;
		case 1: // using probability
                    double randomValue = random.nextDouble();
                        switch (probAskingMethod){
                            case 2: //Weighted complementary Probability 
                                askOracle = randomValue < weightProb * (1.0 - prediction[predictedClass]);
                                System.out.println(askOracle+"  randomValue = " + randomValue + " (1-Pmax) = " + (1.0 - prediction[predictedClass]));
                                break;
                            case 1:  // moderate complementary,                
                                confidenceDegree = confidenceMeasure.getValue(prediction);
                                if (confidenceDegree < confidenceThreshold)
                                    askOracle = true; //
                                else {// it is sure enough
                                    askOracle = randomValue < (1.0 - prediction[predictedClass]);
                                    System.out.println(askOracle+"  randomValue = " + randomValue + " (1-Pmax) = " + (1.0 - prediction[predictedClass]));
                                }
                                break;
                            case 3:  // Threshold + Prob               
                                confidenceDegree = confidenceMeasure.getValue(prediction);
                                if (confidenceDegree < confidenceThreshold)
                                    askOracle = true; //
                                else {// it is sure enough
                                    askOracle = randomValue < weightProb * (1.0 - prediction[predictedClass]);
                                    System.out.println(askOracle+"  randomValue = " + randomValue + " weightProb * (1-Pmax) = " + weightProb * (1.0 - prediction[predictedClass]));
                                }
                                break;                   
                            default: // 0, complementary 
                                askOracle = randomValue < (1.0 - prediction[predictedClass]);
                                System.out.println(askOracle+"  randomValue = " + randomValue + " (1-Pmax) = " + (1.0 - prediction[predictedClass]));
                        }
                    break;
                default: // o case 2 Noask
                    askOracle = false;
                    break;
            } // switch

             //Para control de calidad
            if (instancesProcessed % this.qoaFrequencyOption.getValue() == 0)
                askOracle = true;    
            
            evaluator.addResult(testInst, prediction);
            
            //Aquí ya se aprende o caso contrario no se detectan los warnings ni drifts
            if (!neverLearnOption.isSet()) {
                trainInst.setWeight(trainInst.weight()*weightOracleOption.getValue());
                learner.trainOnInstance(trainInst);
            }            
            
            Measurement[] measure1 = evaluator.getPerformanceMeasurements();
            System.out.println("measure1 " + measure1.length);
            System.out.println(measure1[0].toString());
            System.out.println(measure1[1].toString());
            System.out.println(measure1[2].toString());
            System.out.println(measure1[3].toString());
            System.out.println(measure1[4].toString());
            System.out.println(measure1[5].toString());
            System.out.println(measure1[6].toString());
            System.out.println(measure1[7].toString());
            String meas111 = measure1[0].toString().split("=")[1];
            Double meas11D1 = Double.parseDouble(meas111.replaceAll(",", "."));
            String meas221 = measure1[1].toString().split("=")[1];
            Double meas22D1 = Double.parseDouble(meas221.replaceAll(",", "."));
            String meas331 = measure1[2].toString().split("=")[1];
            Double meas33D1 = Double.parseDouble(meas331.replaceAll(",", "."));
            String meas441 = measure1[3].toString().split("=")[1];
            String meas551 = measure1[4].toString().split("=")[1];
       
            
            Double warningValue = new Double(meas331.trim());
            Double valueToCompare = new Double("0.0");
            
            Double driftValue = new Double(meas551.trim());
                        
            if (askOracle) {
                if (outputPredictionFile != null) { // Output prediction
                    outputPredictionResultStream.println(confidenceMeasure.getValue(predictionOracle) + " " + moa.core.Utils.maxIndex(predictionOracle) + " " + trueClassInteractive + " " + confidenceThreshold +".."+instancesProcessed );
                }
                
                //N -----> W
                //Aquí se pregunta si hay warning y no drift y si se consulta al oráculo
                //en tal caso se agrega a un arrayList de warnings que se van acumulando
                if (!valueToCompare.equals(warningValue) && driftValueToCompare.equals(driftValue)) {
                    InstanceExample trainInstExampleWarning = (InstanceExample)trainInst;
                    Instance insWarning = trainInstExampleWarning.instance;
                    Instance insWarning1 = insWarning;
                    trainInstancesWarning.add(insWarning1);
                    
                    trainExamplesWarning.add(trainInst);
                                                   
                    //Además se guarda en una lista ordenada la clase verdadera,
                    //el vector de predicciones y el número de la instancia procesada
                    WarningPrediction warningPrediction = new WarningPrediction();
                    warningPrediction.trueClassInteractive = trueClassInteractive;
                    warningPrediction.copyPrediction = copyPrediction;
                    warningPrediction.instancesProcessed = instancesProcessed;
                
                    warningPredictions.add(warningPrediction);
                
                    System.out.println("warning detectado que consulta al oráculo en la instancia " + instancesProcessed);
                    //Este sleep solo sirve para seguirle la pista al warning para ver en el terminal
                    //sacar esto para mejorar el performance en la ejecución
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(EvaluateInteractiveConceptDrift.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                
                //W -------> N
                //se desactiva el warning, por lo tanto se vacía el caché de warning (trainInstancesWarning) y no hay drift tampoco
                if (valueToCompare.equals(warningValue) && driftValueToCompare.equals(driftValue)) {
                    trainInstancesWarning = null;
                    trainInstancesWarning = new Instances(stream.getHeader(), maxInstances);
                    trainExamplesWarning = null;
                    trainExamplesWarning = new ArrayList<Example>();
                    warningPredictions.clear();
                }
            }
            
            //N ------> D
            //Aquí se pregunta si hay drift        
            if (!driftValueToCompare.equals(driftValue)) {
                ifDrift = true;
                siCambio = true;
                driftValueToCompare = driftValue;
                //instancesProcessed = 0;
                tempInstancesProcessed = 0;
                interactions = 0;
                successes = 0;
                fails = 0;   
                System.out.println("hay drifttttttttttttttttttttttttt");
                try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(EvalPreqRenew.class.getName()).log(Level.SEVERE, null, ex);
                    }
                //Si hay warning previos se vacían las instancias aprendidas hasta el momento
                //antes del drift y todos los warnings previos se cargan al arrayList principal
                //que es trainInstances
                if (trainInstancesWarning.size() > 0) {
                    trainInstances = null;
                    trainInstances = new Instances(stream.getHeader(), maxInstances);
                    globalPredictions.clear();
                    System.out.println("Cuando hay drift y existen warning previos");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(EvalPreqRenew.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    
                    for (Instance j: trainInstancesWarning) {
                        trainInstances.add(j);
                    }
                    
                    for (Example e: trainExamplesWarning) {
                        trainExamples.add(e);
                    }
                    
                    //aquí se vacía trainInstancesWarning para poder cargar en esta variable
                    //los próximos warnings que podrían haber y también se vacían las predicciones
                    //que se tienen hasta el momento
                    trainInstancesWarning = null;
                    trainInstancesWarning = new Instances(stream.getHeader(), maxInstances);
                    trainExamplesWarning = null;
                    trainExamplesWarning = new ArrayList<Example>();
                    warningPredictions.clear();
                } else {
                    //acá se vacía también trainInstances porque hay drift
                    trainInstances = null;
                    trainInstances = new Instances(stream.getHeader(), maxInstances);
                    trainExamples = null;
                    trainExamples = new ArrayList<Example>();
                    //también se vacían las predicciones globales tenidas hasta el momento
                    globalPredictions.clear();
                }
            }
            
            //Si existe drift y el parámetro de control de drift todavía no es igual al contador
            //este se incrementa y se consulta al oráculo estableciendo la variable
            //askOracle igual a verdadero
            if (ifDrift == true && controllerCountDrift < driftingOption.getValue()) {
                controllerCountDrift = controllerCountDrift + 1;
                askOracle = true;
            } else {
                //si ya pasó la cantidad se coloca de vuelta a 0 el contador
                //y la bandera de cuando es drift se pone a falso
                if (controllerCountDrift >= driftingOption.getValue()) {
                    controllerCountDrift = 0;
                    System.out.println("controllerCountDrift vuelve a 0 " + controllerCountDrift);
                    ifDrift = false;
                }
            }
            
            // Forzamos la clase verdadera
            if (askOracle){
                //Aquí a veces fallaba cuando se probaba con EDDM
                //array out of bounds daba
                
                //if (prediction.length == 2)
                    prediction[trueClassInteractive] = 1;
            	
                interactions++;
            }
            else{
                if (predictedClass == trueClassInteractive){
                    successes++;
                }
                else{
                    fails++;
                }
            }
                                                      
            if (askOracle && !neverLearnOption.isSet()){
            	//System.out.println("Incorporando nueva información al modelo");
            	//Este sería el camino normal, cuando puede que haya un warning
                //pero no drift o puede que no haya ninguno de los dos.
                //De igual manera se utiliza un archivo extra para el procesamiento de las instancias
                //con warning y drift más las instancias que se preguntaron al oráculo
                //de acuerdo al valor que se le da al parámetro driftingOption, incluso también
                //este archivo se utiliza para las instancias que no se preguntaron
                //para hacer coincidir la cantidad total de instancias manejadas, 161000 en nuestro caso
                if(dynamicThresholdOption.isSet() && ifDrift == false ){
                    
                    InstanceExample trainInstExample1 = (InstanceExample)trainInst;
                    Instance ins11 = trainInstExample1.instance;
                    Instance ins1 = ins11;
                    trainInstances.add(ins1);
                   
                    trainExamples.add(trainInst);
                                                            
                    int sizeDriftSet = trainInstances.size() + 1;
            		
                    Prediction p = new Prediction();
                    if (siCambio == false)
                        p.numInstance = (int) instancesProcessed;
                    else
                        p.numInstance = (int) tempInstancesProcessed;
                    p.confidenceValue = confidenceMeasure.getValue(copyPrediction);
                    p.loss = predictedClass == trueClassInteractive;
                    globalPredictions.add(p);
                	
                    confidenceThreshold = calculateDynamicThreshold((Classifier)learner, emicMeasure,
            		confidenceMeasure, sizeDriftSet, confidenceThreshold);
            	
                    
                    if(summarizeTrainingSetOption.isSet()){
            		summarizeTrainingSet(confidenceThreshold);
                    }
            	}
                
                if(dynamicThresholdOption.isSet() && ifDrift == true ) {
                   System.out.println("paruuuuuuuuuuuuuuuuuuuuuuuuuuuuul");
                   try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(EvalPreqRenew.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    //las n instancias incluyendo la instancia del drift son asignadas aquí
                    //y luego se agregan a trainInstances
                    InstanceExample trainInstExample1 = (InstanceExample)trainInst;
                    Instance ins11 = trainInstExample1.instance;
                    Instance ins1 = ins11;
                    trainInstances.add(ins1);
                    
                    trainExamples.add(trainInst);    
                                      
                    //Acá hace falta hacer el cálculo del umbral, si o no, pregunta para la profesora Sylvia?
                    int sizeDriftSet = trainInstances.size() + 1;
            		
                    Prediction p = new Prediction();
                    if (siCambio == false)
                        p.numInstance = (int) instancesProcessed;
                    else
                        p.numInstance = (int) tempInstancesProcessed;
                    
                    p.confidenceValue = confidenceMeasure.getValue(copyPrediction);
                    p.loss = predictedClass == trueClassInteractive;
                    globalPredictions.add(p);
                	
                    confidenceThreshold = calculateDynamicThreshold((Classifier)learner, emicMeasure,
            		confidenceMeasure, sizeDriftSet, confidenceThreshold);
            	
                    System.out.println("interactions PARUL " + interactions);
                    System.out.println("successes PARUL " + successes);
                    System.out.println("fails PARUL " + fails);
                    System.out.println("emic value PARUL " + emicMeasure.getValue());
                    tempEmic = emicMeasure.getValue();
                                            
                    if(summarizeTrainingSetOption.isSet()){
            		summarizeTrainingSet(confidenceThreshold);
                    }
                }
            }
            
            instancesProcessed++;
            tempInstancesProcessed++;
            
            // Actualizamos la EMIC, se usa para Interactive
            if (siCambio == false)
                emicMeasure.setTotal((int)instancesProcessed);
            else
                emicMeasure.setTotal((int)tempInstancesProcessed);
            emicMeasure.setInteractions(interactions);
            emicMeasure.setSuccesses(successes);
            emicMeasure.setFails(fails);
            
            if (instancesProcessed % this.sampleFrequencyOption.getValue() == 0
                    || stream.hasMoreInstances() == false) {
                long evaluateTime = TimingUtils.getNanoCPUTimeOfCurrentThread();
                double time = TimingUtils.nanoTimeToSeconds(evaluateTime - evaluateStartTime);
                double timeIncrement = TimingUtils.nanoTimeToSeconds(evaluateTime - lastEvaluateStartTime);
                double RAMHoursIncrement = learner.measureByteSize() / (1024.0 * 1024.0 * 1024.0); //GBs
                RAMHoursIncrement *= (timeIncrement / 3600.0); //Hours
                RAMHours += RAMHoursIncrement;
                lastEvaluateStartTime = evaluateTime;
                learningCurve.insertEntry(new LearningEvaluation(
                        new Measurement[]{
                            new Measurement(
                            "learning evaluation instances",
                            instancesProcessed),
                            new Measurement(
                            "evaluation time ("
                            + (preciseCPUTiming ? "cpu "
                            : "") + "seconds)",
                            time),
                            new Measurement(
                            "model cost (RAM-Hours)",
                            RAMHours),
                            new Measurement(
                                "% interactions",
                                100*(double)interactions/(double)instancesProcessed),
                            new Measurement(
                                "oracle interactions",
                                interactions),
                            new Measurement(
                                "successes",
                                successes),
                            new Measurement(
                                "fails",
                                fails),
                            new Measurement(
                                "emic",
                                emicMeasure.getValue()),
	                    new Measurement(
	                        "threshold",
	                        confidenceThreshold)
                        },
                        evaluator, learner));
                
                
                if (immediateResultStream != null) {
                    if (firstDump) {
                        immediateResultStream.println(learningCurve.headerToString());
                        firstDump = false;
                    }
                    immediateResultStream.println(learningCurve.entryToString(learningCurve.numEntries() - 1));
                    immediateResultStream.flush();
                }
            }
            if (instancesProcessed % INSTANCES_BETWEEN_MONITOR_UPDATES == 0) {
                if (monitor.taskShouldAbort()) {
                    return null;
                }
                long estimatedRemainingInstances = stream.estimatedRemainingInstances();
                if (maxInstances > 0) {
                    long maxRemaining = maxInstances - instancesProcessed;
                    if ((estimatedRemainingInstances < 0)
                            || (maxRemaining < estimatedRemainingInstances)) {
                        estimatedRemainingInstances = maxRemaining;
                    }
                }
                monitor.setCurrentActivityFractionComplete(estimatedRemainingInstances < 0 ? -1.0
                        : (double) instancesProcessed
                        / (double) (instancesProcessed + estimatedRemainingInstances));
                if (monitor.resultPreviewRequested()) {
                    monitor.setLatestResultPreview(learningCurve.copy());
                }
                secondsElapsed = (int) TimingUtils.nanoTimeToSeconds(TimingUtils.getNanoCPUTimeOfCurrentThread()
                        - evaluateStartTime);
            }
        }
        if (immediateResultStream != null) {
            immediateResultStream.close();
        }
        if (outputPredictionResultStream != null) {
            outputPredictionResultStream.close();
        }
        return learningCurve;
    }
    
    private double calculateThreshold(Classifier learnerInteractive, EMIC emicMeasure,
			ConfidenceMeasure confidenceMeasure, long sizeTrainingSet,
			Instances trainInstances, double previousConfidenceThreshold) {
		
	//Abrimos el fichero donde se guardan las probabilidades, clase verdadera y clase predicha.
        File outputProbabilityFile = this.outputProbabilityFileOption.getFile();
        PrintStream outputProbabilityResultStream = null;
        if (outputProbabilityFile != null) {
            try {
                if (outputProbabilityFile.exists()) {
                	outputProbabilityResultStream = new PrintStream(
                            new FileOutputStream(outputProbabilityFile, true), true);
                } else {
                	outputProbabilityResultStream = new PrintStream(
                            new FileOutputStream(outputProbabilityFile), true);
                }
            } catch (Exception ex) {
                throw new RuntimeException(
                        "Unable to open probability result file: " + outputProbabilityResultStream, ex);
            }
        }
        
        //ArrayList<Prediction> predictions = new ArrayList<Prediction>(sizeTrainingSet);
        int numInstance = 0;
        for (Iterator it = trainInstances.iterator(); it.hasNext();) {
            Instance inst = (Instance) it.next();
            double[] probabilities = learnerInteractive.getVotesForInstance(inst);
            String probString = "[";
            try{
                moa.core.Utils.normalize(probabilities); //normalizamos las probabilidades para que la suma sea 1
            } catch (Exception e) {
                // el perceptron nos puede devolver todas las probabilidades a 0
                for (int i = 0; i < probabilities.length; i++)
                    probabilities[i] = 1.0/inst.numClasses();
                
            }
            for(double pTmp : probabilities){
                probString += pTmp + " ";
            }
            probString += "]";
            int trueClass = (int) inst.classValue();
            EvaluateInteractiveConceptDrift.Prediction p = new EvaluateInteractiveConceptDrift.Prediction();
            p.numInstance = numInstance;
            p.confidenceValue = confidenceMeasure.getValue(probabilities);
            p.loss = moa.core.Utils.maxIndex(probabilities) == trueClass;
            globalPredictions.add(p);
            numInstance++;
            // Sacamos los valores a un fichero para comprobar el cálculo del umbral con el script en perl
            if (outputProbabilityFile != null) {
                outputProbabilityResultStream.println(p.confidenceValue + " " + moa.core.Utils.maxIndex(probabilities) + " " + trueClass + " " + probString);
            }
        } /*
        Prediction p = new Prediction(); // PURACION
        Iterator<Prediction> it = globalPredictions.iterator();
        while (it.hasNext()) {
        p = it.next();
        outputProbabilityResultStream.println(p.confidenceValue + " " + p.loss + " " + p.numInstance );
        } // */ // PURACION
        
        // Cerramos el fichero
        if (outputProbabilityResultStream != null) {
        	outputProbabilityResultStream.close();
        }
        
        return calculateDynamicThreshold(learnerInteractive, emicMeasure,
    			confidenceMeasure, sizeTrainingSet,
    			previousConfidenceThreshold);
    }
    
    private double calculateDynamicThreshold(Classifier learnerInteractive, EMIC emicMeasure,
			ConfidenceMeasure confidenceMeasure, long sizeTrainingSet,
			double previousConfidenceThreshold) {
		       
        // Ordenamos por probabilidad
        //Collections.sort(globalPredictions, new sortByReverseProbability());
        
        // Vamos calculando la medida EMIC de forma iterativa
        //double[] emicMeasureValues = new double[sizeTrainingSet];
        emicMeasure.setTotal(sizeTrainingSet);
        emicMeasure.setInteractions(sizeTrainingSet - sizeSummaryData);
        emicMeasure.setSuccesses(successesSummaryData);
        emicMeasure.setFails(failsSummaryData);
        double bestEmicValue = emicMeasure.getValue();
        double bestConfidenceThreshold = previousConfidenceThreshold;
        double previousThresholdValue = 1.000001;
        long bestSuccesses = 0;
        long bestFails = 0;
        long bestInteractions = 0;
        long bestTotal = 0;
        long successes = successesSummaryData;
        long fails = failsSummaryData;
        
        Iterator<EvaluateInteractiveConceptDrift.Prediction> it = globalPredictions.iterator();
        int tam = globalPredictions.size();
        int i=sizeSummaryData;
        while (it.hasNext()) {
            // Get element
            EvaluateInteractiveConceptDrift.Prediction p = it.next();
            
        	double currentThresholdValue = p.confidenceValue;
        	
        	if (currentThresholdValue < previousThresholdValue){
        		
                    double currentEmicValue = emicMeasure.getValue();
            //	print STDOUT $EMIC,"\t",$noclasificados,"\t",$exitos,"\t",$fracasos,"\t", $EMIC,"\t",$oldv."\n";

           //    System.out.println( currentEmicValue + "       " + emicMeasure.getInteractions() + "         " + emicMeasure.getSuccesses() + "     " + emicMeasure.getFails()+"     "+  previousThresholdValue );

                    if(currentEmicValue>bestEmicValue){
                        bestEmicValue = currentEmicValue;
                        bestConfidenceThreshold = previousThresholdValue; // currentThresholdValue; cambio!!!!
                        bestSuccesses = emicMeasure.getSuccesses();
                        bestFails = emicMeasure.getFails();
                        bestInteractions = emicMeasure.getInteractions();
                        bestTotal = emicMeasure.getTotal();
                    }
        	}
        	
        	emicMeasure.setInteractions(sizeTrainingSet-(i+1));
        	if (p.loss){
                    successes++;
                    emicMeasure.setSuccesses(successes);
        	}
                else {
                    fails++;
                    emicMeasure.setFails(fails);
        	}
        	
        	previousThresholdValue = currentThresholdValue;
        	
        	i++;
        }
        
        // última iteración
        double currentEmicValue = emicMeasure.getValue();
    	
    	if(currentEmicValue>bestEmicValue){
            bestEmicValue = currentEmicValue;
            bestConfidenceThreshold = previousThresholdValue;
            bestSuccesses = emicMeasure.getSuccesses();
            bestFails = emicMeasure.getFails();
            bestInteractions = emicMeasure.getInteractions();
            bestTotal = emicMeasure.getTotal();
    	}
        
        // --------- DEBUG -------------
        if (bestConfidenceThreshold > previousConfidenceThreshold){
            Iterator<EvaluateInteractiveConceptDrift.Prediction> itDEBUG = globalPredictions.iterator();
            while (itDEBUG.hasNext()) {
        	EvaluateInteractiveConceptDrift.Prediction p = itDEBUG.next();
                System.out.println(p.confidenceValue);
            }
            System.out.println("Se incremental el umbral!!!!!!!!!!!");
        }
        // --------- FIN DEBUG -------------
        
        // int tam2 = globalPredictions.size();
         System.out.println("size=" + i + " /  bestConfidenceThreshold= "+ bestConfidenceThreshold + ", bestSuccesses=" + bestSuccesses + ", bestFails=" + bestFails + ", bestInteractions=" +
        		bestInteractions + ", bestTotal=" + bestTotal + ", bestEmicValue=" + bestEmicValue);
        if (this.changeThresholdIfBetterEmicOption.isSet() && this.previousBestEmicValue > bestEmicValue){
        	return previousConfidenceThreshold;
        }
        
        this.previousBestEmicValue = bestEmicValue;
        
        return bestConfidenceThreshold;		
    }
    
    private double calculateWeightProb(Classifier learnerInteractive, EMIC emicMeasure, ConfidenceMeasure confidenceMeasure, Random random,
			 long sizeTrainingSet, Instances trainInstances, int k, int niteraciones) {
        double w, gamma, step = 1.0/k;
        System.out.println("calculateWeightProb"+step+"\n" );
        int l, pos, Mpos;
        double randomValue;
        random.setSeed(3);
      
        int predictedClass;
        int trueClass;
        boolean askOracle = false;
        double[] mediaF = new  double[ k ];
      
        EMIC[] emicMeasures = new EMIC[k];
        long[] interactions = new  long[ k ];
        long[] successes = new  long[ k ];
        long[] fails = new  long[ k ];
  
        for(pos=0;pos<k;pos++) { 
            mediaF[pos]=0.0;
            emicMeasures[pos] = (EMIC) emicMeasure.copy();
        } //inicialization

        for (l=0; l< niteraciones; l++)  { //for1 l, repeat niteration in order to get an average  
            for(pos= 0; pos <k; pos++) {
                interactions[pos]=0; successes[pos]=0; fails[pos]=0;
            }
            long  numInstance = 0;
            
            for (Iterator it = trainInstances.iterator(); it.hasNext();) {
                Instance inst = (Instance)it.next();
              // for2 ninstances
                double[] probabilities = learnerInteractive.getVotesForInstance(inst);
                String probString = "[";
              
                try{
                    moa.core.Utils.normalize(probabilities); //normalizamos las probabilidades para que la suma sea 1
                } catch (Exception e) {
                  // el perceptron nos puede devolver todas las probabilidades a 0
                    for (int i = 0; i < probabilities.length; i++)
                        probabilities[i] = 1.0/inst.numClasses();
                } // catch
              
                for (double pTmp : probabilities){
                    probString += pTmp + " ";
                }
                probString += "]";
                trueClass = (int) inst.classValue();
                predictedClass = moa.core.Utils.maxIndex(probabilities);
                randomValue = random.nextDouble();
                
                for (gamma = step, pos=0;  pos < k; gamma+=step, pos++) { // for3
                    askOracle = randomValue < (1.0 - probabilities[predictedClass])*gamma;
                  
                  // Forzamos la clase verdadera
                    if (askOracle){
                      // probabilities[trueClass] = 1;
                        interactions[pos]++;
                    }
                    else {
                        if (predictedClass == trueClass){
                            successes[pos]++;
                        }
                        else fails[pos]++;
                    }
                  
                } //for3 gamma
                numInstance++;
//               System.out.println("\n 333  "+ randomValue+" \n");

//            for (pos=0;  pos < k; pos++) { // for 2
//                 System.out.println("gam"+ (pos+1)*step + " ni=" +interactions[pos] + " nc=" +successes[pos]+ " nw=" + fails[pos]);

//            }
            } //for2   
            
            System.out.println("\n\n");
            
            for (pos=0;  pos < k; pos++) { // for 2 
                emicMeasures[pos].setFails(fails[pos]);
                emicMeasures[pos].setInteractions(interactions[pos]);
                emicMeasures[pos].setSuccesses(successes[pos]);
                emicMeasures[pos].setTotal(numInstance);
                double fb = emicMeasures[pos].getValue();
                System.out.println("gam"+ (pos+1)*step + " ni=" +interactions[pos] + " nc=" +successes[pos]+ " nw=" + fails[pos]+ "Fb" + fb);

                mediaF[pos]+= fb;
            }         
        } //for1
        
        gamma = -1.0;  // un número negativo fuera del rango ]0..1]
        for (Mpos = 0, pos=0;  pos < k; pos++) { // for3  
            mediaF[pos]/= niteraciones;
            if (gamma < mediaF[pos]){
                gamma = mediaF[pos];
                Mpos = pos;
            }
        }
        
        gamma = step* (1+Mpos);
        System.out.println("*** gamma"+ gamma+ "\n");
        return gamma;
    }
    
    /*
    * Resumimos y eliminamos los datos que sean mayores que el umbral,
    * ya que teóricamente hemos demostrado que no puede aumentar.
    * De esta manera, podremos optimizar el procesamiento al tener
    * menos datos en el conjunto.
    */
    private void summarizeTrainingSet(double threshold){
	
        Iterator<EvaluateInteractiveConceptDrift.Prediction> it = globalPredictions.iterator();
		
	while(it.hasNext()){
            EvaluateInteractiveConceptDrift.Prediction p = it.next();
			
            if (p.confidenceValue > threshold){
		sizeSummaryData++;
				
		if (p.loss) {
                    successesSummaryData++;
		}
                else {
                    failsSummaryData++;
		}
		it.remove();
            }
            else {
		break;
            }
			
	}
    }
}

