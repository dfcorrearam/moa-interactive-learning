/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package moa.tasks;

import com.github.javacliparser.FileOption;
import com.github.javacliparser.FlagOption;
import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.github.javacliparser.MultiChoiceOption;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.InstancesHeader;
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
import moa.core.Examples;
import moa.core.ExamplesHeader;
import moa.core.InstanceExample;
import moa.core.Measurement;
import moa.core.ObjectRepository;
import moa.core.TimingUtils;
import moa.core.Utils;
import moa.evaluation.ClassificationPerformanceEvaluator;
import moa.evaluation.EWMAClassificationPerformanceEvaluator;
import moa.evaluation.FadingFactorClassificationPerformanceEvaluator;
import moa.evaluation.LearningCurve;
import moa.evaluation.LearningEvaluation;
import moa.evaluation.LearningPerformanceEvaluator;
import moa.evaluation.WindowClassificationPerformanceEvaluator;
import moa.learners.Learner;
import moa.options.ClassOption;
import moa.streams.ExampleStream;
import moa.streams.InstanceStream;
import moa.streams.InstanceStreamOld;
import static moa.tasks.MainTask.INSTANCES_BETWEEN_MONITOR_UPDATES;

/**
 *
 * @author spark
 */
public class EvalPreqRenew extends MainTask {

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

    //class option learner using for interactive and prequential
    public ClassOption learnerOption = new ClassOption("learner", 'l',
            "Learner to train.", Classifier.class, "moa.classifiers.bayes.NaiveBayes");

    //class option stream using for interactive and prequential
    public ClassOption streamOption = new ClassOption("stream", 's',
            "Stream to learn from.", ExampleStream.class,
            "generators.RandomTreeGenerator");

    //Prequential class option using for evaluation
    public ClassOption evaluatorOption = new ClassOption("evaluator", 'e',
            "Classification performance evaluation method.",
            LearningPerformanceEvaluator.class,
            "WindowClassificationPerformanceEvaluator");
    
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

    //both Interactive and Prequential
    public IntOption instanceLimitOption = new IntOption("instanceLimit", 'i',
            "Maximum number of instances to test/train on  (-1 = no limit).",
            100000000, -1, Integer.MAX_VALUE);

    
    //both Interactive and Prequential
    public IntOption timeLimitOption = new IntOption("timeLimit", 't',
            "Maximum number of seconds to test/train for (-1 = no limit).", -1,
            -1, Integer.MAX_VALUE);

    //just an observation: in the fourth parameter exists difference between interactive and prequential
    //class starting 1 in interactive and 100000 in prequential
    public IntOption sampleFrequencyOption = new IntOption("sampleFrequency",
            'f',
            "How many instances between samples of the learning performance.",
            100000, 0, Integer.MAX_VALUE);

    //Prequential option using for Memory check frequency
    public IntOption memCheckFrequencyOption = new IntOption(
            "memCheckFrequency", 'q',
            "How many instances between memory bound checks.", 100000, 0,
            Integer.MAX_VALUE);

    //Prequential and interactive option using to dump a file
    //but it must be controlled if what information is saved here to join
    //both data output
    public FileOption dumpFileOption = new FileOption("dumpFile", 'd',
            "File to append intermediate csv results to.", null, "csv", true);

    //Prequential and interactive option using to dump a file
    public FileOption outputPredictionFileOption = new FileOption("outputPredictionFile", 'o',
            "File to append output predictions to.", null, "pred", true);
    
    //Interactive option using to dump a file with probabilities values  
    public FileOption outputProbabilityFileOption = new FileOption("outputProbabilityFile", 'p',
            "File to append output probability (and predicted and true class) to.", null, "prob", true);
    
    //New for prequential method DEPRECATED
    public IntOption widthOption = new IntOption("width",
            'w', "Size of Window", 1000);

    //Prequential option using for fading factor
    public FloatOption alphaOption = new FloatOption("alpha",
            'a', "Fading factor or exponential smoothing factor", .01);
    //End New for prequential methods
    
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
        //Using for prequential, example NaiveBayes
        Learner learner = (Learner) getPreparedClassOption(this.learnerOption);        
        //Using for Prequential
        ExampleStream stream = (ExampleStream) getPreparedClassOption(this.streamOption);
        //Using for Prequential
        LearningPerformanceEvaluator evaluator = (LearningPerformanceEvaluator) getPreparedClassOption(this.evaluatorOption);
        //Using for Prequential learningCurve
        LearningCurve learningCurve = new LearningCurve(
                "learning evaluation instances (Prequential)");
        
        
        
        //New for prequential methods
        if (evaluator instanceof WindowClassificationPerformanceEvaluator) {
            //((WindowClassificationPerformanceEvaluator) evaluator).setWindowWidth(widthOption.getValue());
            if (widthOption.getValue() != 1000) {
                System.out.println("DEPRECATED! Use EvaluatePrequential -e (WindowClassificationPerformanceEvaluator -w " + widthOption.getValue() + ")");
                 return learningCurve;
            }
        }
        if (evaluator instanceof EWMAClassificationPerformanceEvaluator) {
            //((EWMAClassificationPerformanceEvaluator) evaluator).setalpha(alphaOption.getValue());
            if (alphaOption.getValue() != .01) {
                System.out.println("DEPRECATED! Use EvaluatePrequential -e (EWMAClassificationPerformanceEvaluator -a " + alphaOption.getValue() + ")");
                return learningCurve;
            }
        }
        if (evaluator instanceof FadingFactorClassificationPerformanceEvaluator) {
            //((FadingFactorClassificationPerformanceEvaluator) evaluator).setalpha(alphaOption.getValue());
            if (alphaOption.getValue() != .01) {
                System.out.println("DEPRECATED! Use EvaluatePrequential -e (FadingFactorClassificationPerformanceEvaluator -a " + alphaOption.getValue() + ")");
                return learningCurve;
            }
        }
        //End New for prequential methods
        
        //using for Prequential
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
        while (stream.hasMoreInstances() && instancesTrained < sizeTrainingSet) {
        	Example trainInst = stream.nextInstance();
                
                InstanceExample trainInstExample = (InstanceExample)trainInst;
                Instance ins1 = trainInstExample.instance;
                Instance ins = ins1;
        	trainInstances.add(ins);
                trainExamples.add(trainInst);
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
            weightProb =  calculateWeightProb((Classifier)learner, emicMeasure,
				confidenceMeasure, random, instancesTrained, trainInstances, 10, 20);
            System.out.println("@@@ weightProb"+weightProb);

        }
        if(summarizeTrainingSetOption.isSet()){
            summarizeTrainingSet(confidenceThreshold);
        }
        
                
        //Using for Prequential
        int maxInstances = this.instanceLimitOption.getValue();
        //Using for Interactive
        int successes = 0;
        //Using for Interactive
        int fails = 0;
        //Using for Interactive
        int interactions = 0;
        //Using for Prequential and Interactive too and the three more lines of code
        long instancesProcessed = 0;
        int maxSeconds = this.timeLimitOption.getValue();
        int secondsElapsed = 0;
        monitor.setCurrentActivity("Evaluating learner...", -1.0);

        int successesLocal = 0;
        //Using for Interactive
        int failsLocal = 0;
        //Using for Interactive
        int interactionsLocal = 0;
        
        //Using for Prequential
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
        //File for output predictions using for Prequential
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
        
        File warningDriftFile = this.warningDriftFileOption.getFile();
        PrintStream warningDriftResultStream = null;
        if (warningDriftFile != null) {
            try {
                if (warningDriftFile.exists()) {
                    warningDriftResultStream = new PrintStream(
                            new FileOutputStream(warningDriftFile, true), true);
                } else {
                    warningDriftResultStream = new PrintStream(
                            new FileOutputStream(warningDriftFile), true);
                }
            } catch (Exception ex) {
                throw new RuntimeException(
                        "Unable to open warningDrift result file: " + warningDriftFile, ex);
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

        int controllerCountDrift = 0;
        boolean ifDrift = false;
        double tempEmic = 0;
        //Validar especificación de parámetro en la línea de comando para evitar error de
        //instanciación de variables
        //por ejemplo 100 el valor del parámetro driftingOption + lo acumulado del warning
        Instances trainInstancesWarning = new Instances(stream.getHeader(), maxInstances);     
                    
        while (stream.hasMoreInstances()
        && ((maxInstances < 0) || (instancesProcessed < maxInstances))
        && ((maxSeconds < 0) || (secondsElapsed < maxSeconds))) {
            Example trainInst = stream.nextInstance();
            Example testInst = (Example) trainInst; //.copy();
            //Using for Interactive
            int trueClassInteractive = (int) ((Instance) trainInst.getData()).classValue();
            //testInst.setClassMissing();
            //Using for Prequential
            double[] prediction = learner.getVotesForInstance(testInst);
            // Output prediction
            if (outputPredictionFile != null) {
                int trueClass = (int) ((Instance) trainInst.getData()).classValue();
                outputPredictionResultStream.println(Utils.maxIndex(prediction) + "," + (
                 ((Instance) testInst.getData()).classIsMissing() == true ? " ? " : trueClass));
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
//            System.out.println("askingMethod"+askingMethod);
            
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

            System.out.println("trueClassInteractive " + trueClassInteractive + " prediction " + prediction[0]);
            
            //Para control de calidad
            if (instancesProcessed % this.qoaFrequencyOption.getValue() == 0)
                askOracle = true;               

            evaluator.addResult(testInst, prediction);     
            
            //Aquí ya se aprende o caso contrario no se detectan los warnings ni drifts
            if (!neverLearnOption.isSet()) {
                trainInst.setWeight(trainInst.weight()*weightOracleOption.getValue());
                learner.trainOnInstance(trainInst);
            }
            
            Measurement[] measLearnerEstad2 = learner.getModelMeasurements();             
            Double warningValue = new Double(measLearnerEstad2[3].toString().split("=")[1].trim());
            Double valueToCompare = new Double("0.0");
            
            Double driftValue = new Double(measLearnerEstad2[2].toString().split("=")[1].trim());
            Double driftValueToCompare = new Double("0.0");
            
            if (askOracle) {
                if (outputPredictionFile != null) { // Output prediction
                    outputPredictionResultStream.println(confidenceMeasure.getValue(prediction) + " " + moa.core.Utils.maxIndex(prediction) + " " + trueClassInteractive + " " + confidenceThreshold +".."+instancesProcessed );
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
                        Logger.getLogger(EvalPreqRenew.class.getName()).log(Level.SEVERE, null, ex);
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
                
                instancesProcessed = 0;
                
                interactions = 0;
                successes = 0;
                fails = 0;   
                                                          
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
                
                if (prediction.length == 2)
                    prediction[trueClassInteractive] = 1;
            	
                interactions++;
                interactionsLocal++;
            }
            else{
                if (predictedClass == trueClassInteractive){
                    successes++;
                    successesLocal++;
                }
                else{
                    fails++;
                    failsLocal++;
                }
            }
            
            double instancesProcessedD1 = new Long(instancesProcessed).doubleValue();
                                         
            Measurement[] measure1 = evaluator.getPerformanceMeasurements();
                
            String meas111 = measure1[0].toString().split("=")[1];
            Double meas11D1 = Double.parseDouble(meas111.replaceAll(",", "."));
            String meas221 = measure1[1].toString().split("=")[1];
            Double meas22D1 = Double.parseDouble(meas221.replaceAll(",", "."));
            String meas331 = measure1[2].toString().split("=")[1];
            Double meas33D1 = Double.parseDouble(meas331.replaceAll(",", "."));
            String meas441 = measure1[3].toString().split("=")[1];
            String meas551 = measure1[4].toString().split("=")[1];
                
            double interactionsD1 = (double)interactions;
            double successesD1 = (double)successes;
            double failsD1 = (double)fails;
            double percentageInteractions1 = 100*(double)interactions/(double)instancesProcessed;
            String perInteractions1 =  String.valueOf(percentageInteractions1);
                    
            if (askOracle && !neverLearnOption.isSet()) {
            	//System.out.println("Incorporando nueva información al modelo");
            	
            	// Recalculamos el umbral añadiendo al conjunto de entrenamiento la nueva instancia conocida
            	
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
                    p.numInstance = (int) instancesProcessed;
                    p.confidenceValue = confidenceMeasure.getValue(copyPrediction);
                    p.loss = predictedClass == trueClassInteractive;
                    globalPredictions.add(p);
                	
                    confidenceThreshold = calculateDynamicThreshold((Classifier)learner, emicMeasure,
            		confidenceMeasure, sizeDriftSet, confidenceThreshold);
            	
                    warningDriftResultStream.println(instancesProcessedD1 + ",  ,   ," + perInteractions1 + "," + interactionsD1 + "," + successesD1 + "," + failsD1 + "," + emicMeasure.getValue() + "," + confidenceThreshold + "," + meas11D1 + "," + meas22D1 + "," + meas33D1 + "," + measLearnerEstad2[0].toString().split("=")[1] + "," + measLearnerEstad2[1].toString().split("=")[1] + "," + measLearnerEstad2[2].toString().split("=")[1] + "," + measLearnerEstad2[3].toString().split("=")[1] + "," + askOracle + ",sin drift," + trainInstances.size());
                    warningDriftResultStream.flush();  
                    
                    if(summarizeTrainingSetOption.isSet()){
            		summarizeTrainingSet(confidenceThreshold);
                    }
            	}
                
                if(dynamicThresholdOption.isSet() && ifDrift == true ) {
                   
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
                    p.numInstance = (int) instancesProcessed;
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
                                        
                    warningDriftResultStream.println(instancesProcessedD1 + ",  ,   ," + perInteractions1 + "," + interactionsD1 + "," + successesD1 + "," + failsD1 + "," + emicMeasure.getValue() + "," + confidenceThreshold + "," + meas11D1 + "," + meas22D1 + "," + meas33D1 + "," + measLearnerEstad2[0].toString().split("=")[1] + "," + measLearnerEstad2[1].toString().split("=")[1] + "," + measLearnerEstad2[2].toString().split("=")[1] + "," + measLearnerEstad2[3].toString().split("=")[1] + "," + askOracle + ",con drift," + trainInstances.size());
                    warningDriftResultStream.flush();  
                    
                    if(summarizeTrainingSetOption.isSet()){
            		summarizeTrainingSet(confidenceThreshold);
                    }
                }   
            }
            else {
                warningDriftResultStream.println(instancesProcessedD1 + ",  ,   ," + perInteractions1 + "," + interactionsD1 + "," + successesD1 + "," + failsD1 + "," + emicMeasure.getValue() + "," + confidenceThreshold + "," + meas11D1 + "," + meas22D1 + "," + meas33D1 + "," + measLearnerEstad2[0].toString().split("=")[1] + "," + measLearnerEstad2[1].toString().split("=")[1] + "," + measLearnerEstad2[2].toString().split("=")[1] + "," + measLearnerEstad2[3].toString().split("=")[1] + "," + askOracle + ",con drift," + trainInstances.size());
                warningDriftResultStream.flush();
            }
              
            // Output prediction for Prequential
            if (outputPredictionFile != null) {
                int trueClass = (int) ((Instance) trainInst.getData()).classValue();
                outputPredictionResultStream.println(Utils.maxIndex(prediction) + "," + (
                 ((Instance) testInst.getData()).classIsMissing() == true ? " ? " : trueClass));
            }   
            
            instancesProcessed++;
            
            // Actualizamos la EMIC, se usa para Interactive
            emicMeasure.setTotal((int)instancesProcessed);
            emicMeasure.setInteractions(interactions);
            emicMeasure.setSuccesses(successes);
            emicMeasure.setFails(fails);
                        
            System.out.println("fails ! " + emicMeasure.getFails());
            System.out.println("successes ! " + emicMeasure.getSuccesses());
            System.out.println("interactions ! " + emicMeasure.getInteractions());
            System.out.println("instances processed !" + emicMeasure.getTotal());
            System.out.println("EMIC value " + emicMeasure.getValue());
            try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(EvalPreqRenew.class.getName()).log(Level.SEVERE, null, ex);
                    }
            if (ifDrift == true)
            {
                try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(EvalPreqRenew.class.getName()).log(Level.SEVERE, null, ex);
                    }
            }
            //Using for both Interactive and Prequential
            if (instancesProcessed % this.sampleFrequencyOption.getValue() == 0
                    || stream.hasMoreInstances() == false) 
            {
                long evaluateTime = TimingUtils.getNanoCPUTimeOfCurrentThread();
                double time = TimingUtils.nanoTimeToSeconds(evaluateTime - evaluateStartTime);
                double timeIncrement = TimingUtils.nanoTimeToSeconds(evaluateTime - lastEvaluateStartTime);
                double RAMHoursIncrement = learner.measureByteSize() / (1024.0 * 1024.0 * 1024.0); //GBs
                RAMHoursIncrement *= (timeIncrement / 3600.0); //Hours
                RAMHours += RAMHoursIncrement;
                lastEvaluateStartTime = evaluateTime;
                  
                //Se imprimen con fines de verificación los valores de las columnas
                //para el csv ya que con learningCurve daba un error de insertEntry
                //y generamos de manera manual concatenando las columnas
                
                double instancesProcessedD = new Long(instancesProcessed).doubleValue();
                                         
                Measurement[] measure = evaluator.getPerformanceMeasurements();
                
                String meas11 = measure[0].toString().split("=")[1];
                Double meas11D = Double.parseDouble(meas11.replaceAll(",", "."));
                String meas22 = measure[1].toString().split("=")[1];
                Double meas22D = Double.parseDouble(meas22.replaceAll(",", "."));
                String meas33 = measure[2].toString().split("=")[1];
                Double meas33D = Double.parseDouble(meas33.replaceAll(",", "."));
                String meas44 = measure[3].toString().split("=")[1];
                String meas55 = measure[4].toString().split("=")[1];
                
                if (immediateResultStream != null) {
                    if (firstDump) {
                        //immediateResultStream.println("learning evaluation instances|evaluation time (cpu seconds)|model cost (RAM-Hours)|% interactions|oracle interactions|successes|fails|emic|threshold|classified instances|classifications correct (percent)|Kappa Statistic (percent)|Kappa Temporal Statistic (percent)|Kappa M Statistic (percent)|model training instances|model serialized size (bytes)|Changed detected|Warning detected");
                        immediateResultStream.println("learning evaluation instances,evaluation time (cpu seconds),model cost (RAM-Hours),% interactions,oracle interactions,successes,fails,emic,threshold,classified instances,classifications correct (percent),Kappa Statistic (percent),model training instances,model serialized size (bytes),Changed detected,Warning detected,ask Oracle,% interactions,oracle interactions local,successes local,fails local");
                        firstDump = false;
                    }
                    double interactionsD = (double)interactions;
                    double successesD = (double)successes;
                    double failsD = (double)fails;
                    double percentageInteractions = 100*(double)interactions/(double)instancesProcessed;
                    String perInteractions =  String.valueOf(percentageInteractions);
                    
                    double interactionsDLocal = (double)interactionsLocal;
                    double successesDLocal = (double)successesLocal;
                    double failsDLocal = (double)failsLocal;
                    double percentageInteractionsLocal = 100*(double)interactionsLocal/(double)instancesProcessed;
                    String perInteractionsLocal =  String.valueOf(percentageInteractionsLocal);
                    
                    System.out.println("instancia " + instancesProcessed);
                    
                    immediateResultStream.println(instancesProcessedD + "," + time + "," + RAMHours + "," + perInteractions + "," + interactionsD + "," + successesD + "," + failsD + "," + emicMeasure.getValue() + "," + confidenceThreshold + "," + meas11D + "," + meas22D + "," + meas33D + "," + measLearnerEstad2[0].toString().split("=")[1] + "," + measLearnerEstad2[1].toString().split("=")[1] + "," + measLearnerEstad2[2].toString().split("=")[1] + "," + measLearnerEstad2[3].toString().split("=")[1] + "," + askOracle + "," + perInteractionsLocal + "," + interactionsDLocal + "," + successesDLocal + "," + failsDLocal);                      
                        
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
        
        //se reinicia el evaluador y el aprendedor en base al resumen
        //en los objetivos del TFM
        evaluator.reset();
        learner.resetLearning();
        
        int instancesTrainedDrift = 0;
        instancesProcessed = 0;
        
        System.out.println("Tamaño de trainInstances " + trainInstances.size());
        System.out.println("Tamaño de trainExamples " + trainExamples.size());
        try {
            Thread.sleep(15000);
        } catch (InterruptedException ex) {
            Logger.getLogger(EvalPreqRenew.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        trainInstances = null;
        trainInstances = new Instances(stream.getHeader(), maxInstances);
        globalPredictions.clear();
        interactions = 0;
        interactionsLocal = 0;
        successes = 0;
        successesLocal = 0;
        fails = 0;
        failsLocal = 0;
        int sizeTrainingSet2 = 400;
        //Acá se va a entrenar 400 luego de reiniciar el aprendedor y el evaluador
        //el problema aquí es que se necesita una variable de tipo Example para volver a realizar
        //el aprendizaje con el método learner.trainOnInstance pero en líneas de código más arriba
        //cuando se hace trainExamples.add(trainInst) da error de NullPointerException
        //y no tiene sentido
        
        while (trainExamples.size() > instancesTrainedDrift && instancesTrainedDrift < 400) {
            InstanceExample trainInstExample = (InstanceExample)trainExamples.get(instancesTrainedDrift);
            Instance ins1 = trainInstExample.instance;
            Instance ins = ins1;
            trainInstances.add(ins);
            learner.trainOnInstance(trainExamples.get(instancesTrainedDrift));
            instancesTrainedDrift = instancesTrainedDrift + 1;
        }
        System.out.println("instancesTrainedDrift " + instancesTrainedDrift);
        try {
            Thread.sleep(15000);
        } catch (InterruptedException ex) {
            Logger.getLogger(EvalPreqRenew.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        confidenceThreshold = calculateThreshold((Classifier)learner, emicMeasure, confidenceMeasure, instancesTrainedDrift, trainInstances, 1.0);
        
        if (probAskingMethod > 1) {// weightProb or ThresProb
        // ============== Cálculo de weight/ confidence usando las instancias de training ==============
            weightProb =  calculateWeightProb((Classifier)learner, emicMeasure,
				confidenceMeasure, random, instancesTrainedDrift, trainInstances, 10, 20);
            System.out.println("@@@ weightProb"+weightProb);

        }
        if(summarizeTrainingSetOption.isSet()){
            summarizeTrainingSet(confidenceThreshold);
        }
        
        //Using for Prequential
        int maxInstancesDrift = this.instanceLimitOption.getValue();
        //Using for Interactive
        int successesDrift = 0;
        //Using for Interactive
        int failsDrift = 0;
        //Using for Interactive
        int interactionsDrift = 0;
        //Using for Prequential and Interactive too and the three more lines of code
        long instancesProcessedDrift = 0;
        int maxSecondsDrift = this.timeLimitOption.getValue();
        int secondsElapsedDrift = 0;
        monitor.setCurrentActivity("Re-evaluating learner drift...", -1.0);

        //Using for Prequential
        File dumpFileDrift = this.dumpFileOption.getFile();
        PrintStream immediateResultStreamDrift = null;
        if (dumpFileDrift != null) {
            try {
                if (dumpFileDrift.exists()) {
                    immediateResultStreamDrift = new PrintStream(
                            new FileOutputStream(dumpFileDrift, true), true);
                } else {
                    immediateResultStreamDrift = new PrintStream(
                            new FileOutputStream(dumpFileDrift), true);
                }
            } catch (Exception ex) {
                throw new RuntimeException(
                        "Unable to open immediate result file: " + dumpFileDrift, ex);
            }
        }
        //File for output predictions using for Prequential
        File outputPredictionFileDrift = this.outputPredictionFileOption.getFile();
        PrintStream outputPredictionResultStreamDrift = null;
        if (outputPredictionFileDrift != null) {
            try {
                if (outputPredictionFileDrift.exists()) {
                    outputPredictionResultStreamDrift = new PrintStream(
                            new FileOutputStream(outputPredictionFileDrift, true), true);
                } else {
                    outputPredictionResultStreamDrift = new PrintStream(
                            new FileOutputStream(outputPredictionFileDrift), true);
                }
            } catch (Exception ex) {
                throw new RuntimeException(
                        "Unable to open prediction result file: " + outputPredictionFileDrift, ex);
            }
        }
        
        boolean firstDumpDrift = true;
        boolean preciseCPUTimingDrift = TimingUtils.enablePreciseTiming();
        long evaluateStartTimeDrift = TimingUtils.getNanoCPUTimeOfCurrentThread();
        long lastEvaluateStartTimeDrift = evaluateStartTime;
        double RAMHoursDrift = 0.0;
        
        if (outputPredictionFileDrift != null) { // Output prediction
            outputPredictionResultStreamDrift.println(emicMeasure.getValue()+ "!!!!!!!!!!!!!!!"+confidenceThreshold);
            if (probAskingMethod > 1)
                outputPredictionResultStreamDrift.println("@@@ weightProb"+weightProb);

        }
        
        //Aquí debería seguir el código luego del entrenamiento de las 400 instancias
        //ya que más de 1500 instancias son en total y se extraen 400 para entrenar
        System.out.println("trainExamples sizeeee parul " + trainExamples.size());
        System.out.println("instances trained drift parul " + instancesTrainedDrift);
       
        try {
            Thread.sleep(15000);
        } catch (InterruptedException ex) {
            Logger.getLogger(EvalPreqRenew.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        while (instancesTrainedDrift < trainExamples.size()) {
            System.out.println("instances trained drift " + instancesTrainedDrift);
            Example trainInst = trainExamples.get(instancesTrainedDrift);
            Example testInst = (Example) trainInst; //.copy();
            instancesTrainedDrift = instancesTrainedDrift + 1;
            //Using for Interactive
            int trueClassInteractive = (int) ((Instance) trainInst.getData()).classValue();
            //testInst.setClassMissing();
            //Using for Prequential
            double[] prediction = learner.getVotesForInstance(testInst);
            // Output prediction
            if (outputPredictionFile != null) {
                int trueClass = (int) ((Instance) trainInst.getData()).classValue();
                outputPredictionResultStream.println(Utils.maxIndex(prediction) + "," + (
                 ((Instance) testInst.getData()).classIsMissing() == true ? " ? " : trueClass));
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
//            System.out.println("askingMethod"+askingMethod);
            
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

                      
            
            // Forzamos la clase verdadera
            if (askOracle){
                //Aquí a veces fallaba cuando se probaba con EDDM
                //array out of bounds daba
                
                if (prediction.length == 2)
                    prediction[trueClassInteractive] = 1;
            	
                interactions++;
                interactionsLocal++;
            }
            else{
                if (predictedClass == trueClassInteractive){
                    successes++;
                    successesLocal++;
                }
                else{
                    fails++;
                    failsLocal++;
                }
            }
            
            evaluator.addResult(testInst, prediction);
            if (askOracle && !neverLearnOption.isSet()) {
                //System.out.println("Incorporando nueva información al modelo");
            	trainInst.setWeight(trainInst.weight()*weightOracleOption.getValue());
            	learner.trainOnInstance(trainInst);
            	
                
            	// Recalculamos el umbral añadiendo al conjunto de entrenamiento la nueva instancia conocida
            	if(dynamicThresholdOption.isSet()){
                    sizeTrainingSet2++;

                    Prediction p = new Prediction();
                    p.numInstance = (int) instancesProcessed;
                    p.confidenceValue = confidenceMeasure.getValue(copyPrediction);
                    p.loss = predictedClass == trueClassInteractive;
                    globalPredictions.add(p);

                    confidenceThreshold = calculateDynamicThreshold((Classifier)learner, emicMeasure,
                                    confidenceMeasure, sizeTrainingSet2, confidenceThreshold);

                    if(summarizeTrainingSetOption.isSet()){
                        summarizeTrainingSet(confidenceThreshold);
                    }
                }
            }
            
            Measurement[] measLearnerEstad2 = learner.getModelMeasurements();             

            /*if (askOracle) {
                if (outputPredictionFile != null) { // Output prediction
                    outputPredictionResultStream.println(confidenceMeasure.getValue(prediction) + " " + moa.core.Utils.maxIndex(prediction) + " " + trueClassInteractive + " " + confidenceThreshold +".."+instancesProcessed );
                }
                                         
            }*/
            
            double instancesProcessedD1 = new Long(instancesProcessed).doubleValue();
                                         
            Measurement[] measure1 = evaluator.getPerformanceMeasurements();
                
            String meas111 = measure1[0].toString().split("=")[1];
            Double meas11D1 = Double.parseDouble(meas111.replaceAll(",", "."));
            String meas221 = measure1[1].toString().split("=")[1];
            Double meas22D1 = Double.parseDouble(meas221.replaceAll(",", "."));
            String meas331 = measure1[2].toString().split("=")[1];
            Double meas33D1 = Double.parseDouble(meas331.replaceAll(",", "."));
            String meas441 = measure1[3].toString().split("=")[1];
            String meas551 = measure1[4].toString().split("=")[1];
                
            double interactionsD1 = (double)interactions;
            double successesD1 = (double)successes;
            double failsD1 = (double)fails;
            double percentageInteractions1 = 100*(double)interactions/(double)instancesProcessed;
            String perInteractions1 =  String.valueOf(percentageInteractions1);
                    
            
            // Output prediction for Prequential
            if (outputPredictionFile != null) {
                int trueClass = (int) ((Instance) trainInst.getData()).classValue();
                outputPredictionResultStream.println(Utils.maxIndex(prediction) + "," + (
                 ((Instance) testInst.getData()).classIsMissing() == true ? " ? " : trueClass));
            }   
            
            instancesProcessed++;
            
            // Actualizamos la EMIC, se usa para Interactive
            emicMeasure.setTotal((int)instancesProcessed);
            emicMeasure.setInteractions(interactions);
            emicMeasure.setSuccesses(successes);
            emicMeasure.setFails(fails);
            
            //Actualizamos la EMIC local, se usa para Interactive
            emicMeasure.setInteractionsLocal(interactionsLocal);
            emicMeasure.setSuccessesLocal(successesLocal);
            emicMeasure.setFailsLocal(failsLocal);
            
            //Using for both Interactive and Prequential
            if (instancesProcessed % this.sampleFrequencyOption.getValue() == 0
                    || stream.hasMoreInstances() == false) {
                long evaluateTime = TimingUtils.getNanoCPUTimeOfCurrentThread();
                double time = TimingUtils.nanoTimeToSeconds(evaluateTime - evaluateStartTime);
                double timeIncrement = TimingUtils.nanoTimeToSeconds(evaluateTime - lastEvaluateStartTime);
                double RAMHoursIncrement = learner.measureByteSize() / (1024.0 * 1024.0 * 1024.0); //GBs
                RAMHoursIncrement *= (timeIncrement / 3600.0); //Hours
                RAMHours += RAMHoursIncrement;
                lastEvaluateStartTime = evaluateTime;
                  
                //Se imprimen con fines de verificación los valores de las columnas
                //para el csv ya que con learningCurve daba un error de insertEntry
                //y generamos de manera manual concatenando las columnas
                
                double instancesProcessedD = new Long(instancesProcessed).doubleValue();
                                         
                Measurement[] measure = evaluator.getPerformanceMeasurements();
                
                String meas11 = measure[0].toString().split("=")[1];
                Double meas11D = Double.parseDouble(meas11.replaceAll(",", "."));
                String meas22 = measure[1].toString().split("=")[1];
                Double meas22D = Double.parseDouble(meas22.replaceAll(",", "."));
                String meas33 = measure[2].toString().split("=")[1];
                Double meas33D = Double.parseDouble(meas33.replaceAll(",", "."));
                String meas44 = measure[3].toString().split("=")[1];
                String meas55 = measure[4].toString().split("=")[1];
                
                if (immediateResultStream != null) {
                    if (firstDump) {
                        //immediateResultStream.println("learning evaluation instances|evaluation time (cpu seconds)|model cost (RAM-Hours)|% interactions|oracle interactions|successes|fails|emic|threshold|classified instances|classifications correct (percent)|Kappa Statistic (percent)|Kappa Temporal Statistic (percent)|Kappa M Statistic (percent)|model training instances|model serialized size (bytes)|Changed detected|Warning detected");
                        immediateResultStream.println("learning evaluation instances,evaluation time (cpu seconds),model cost (RAM-Hours),% interactions,oracle interactions,successes,fails,emic,threshold,classified instances,classifications correct (percent),Kappa Statistic (percent),model training instances,model serialized size (bytes),Changed detected,Warning detected,ask Oracle,% interactions,oracle interactions local,successes local,fails local");
                        firstDump = false;
                    }
                    double interactionsD = (double)interactions;
                    double successesD = (double)successes;
                    double failsD = (double)fails;
                    double percentageInteractions = 100*(double)interactions/(double)instancesProcessed;
                    String perInteractions =  String.valueOf(percentageInteractions);
                    
                    double interactionsDLocal = (double)interactionsLocal;
                    double successesDLocal = (double)successesLocal;
                    double failsDLocal = (double)failsLocal;
                    double percentageInteractionsLocal = 100*(double)interactionsLocal/(double)instancesProcessed;
                    String perInteractionsLocal =  String.valueOf(percentageInteractionsLocal);
                    
                    System.out.println("instancia " + instancesProcessed);
                    
                    immediateResultStream.println(instancesProcessedD + "," + time + "," + RAMHours + "," + perInteractions + "," + interactionsD + "," + successesD + "," + failsD + "," + emicMeasure.getValue() + "," + confidenceThreshold + "," + meas11D + "," + meas22D + "," + meas33D + "," + measLearnerEstad2[0].toString().split("=")[1] + "," + measLearnerEstad2[1].toString().split("=")[1] + "," + measLearnerEstad2[2].toString().split("=")[1] + "," + measLearnerEstad2[3].toString().split("=")[1] + "," + askOracle + "," + perInteractionsLocal + "," + interactionsDLocal + "," + successesDLocal + "," + failsDLocal);
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
        if (warningDriftResultStream != null) {
            warningDriftResultStream.close();
        }
        
        if (immediateResultStreamDrift != null) {
            immediateResultStreamDrift.close();
        }
        if (outputPredictionResultStreamDrift != null) {
            outputPredictionResultStreamDrift.close();
        }
                
        return learningCurve;
    }
    
    private double calculateThreshold(Classifier learnerInteractive, EMIC emicMeasure,
			ConfidenceMeasure confidenceMeasure, long sizeTrainingSet,
			Instances trainInstances, double previousConfidenceThreshold) {
		
		// Abrimos el fichero donde se guardan las probabilidades, clase verdadera y clase predicha.
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
        
        
        
//        ArrayList<Prediction> predictions = new ArrayList<Prediction>(sizeTrainingSet);
        int numInstance = 0;
        int albertocontador = 0;
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
                Prediction p = new Prediction();
                p.numInstance = numInstance;
                p.confidenceValue = confidenceMeasure.getValue(probabilities);
                p.loss = moa.core.Utils.maxIndex(probabilities) == trueClass;
                globalPredictions.add(p);
                numInstance++;
                // Sacamos los valores a un fichero para comprobar el cálculo del umbral con el script en perl
                if (outputProbabilityFile != null) {
                    outputProbabilityResultStream.println(p.confidenceValue + " " + moa.core.Utils.maxIndex(probabilities) + " " + trueClass + " " + probString);
                }    
            
        }
        /*
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
        
        Iterator<Prediction> it = globalPredictions.iterator();
        int tam = globalPredictions.size();
        int i=sizeSummaryData;
        while (it.hasNext()) {
            // Get element
            Prediction p = it.next();
            
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
        	else{
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
        	Iterator<Prediction> itDEBUG = globalPredictions.iterator();
        	while (itDEBUG.hasNext()) {
        		Prediction p = itDEBUG.next();
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
    
    
    private double calculateDynamicThresholdLocal(Classifier learnerInteractive, EMIC emicMeasure,
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
        long bestFails = 0