/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package moa.classifiers.core.driftdetection;

import com.github.javacliparser.IntOption;
import moa.core.ObjectRepository;
import moa.tasks.TaskMonitor;
/**
 *
 * @author spark
 */
public class SimpleDetector extends AbstractChangeDetector {
    
    private static final long serialVersionUID = -3518369648142099719L;
    
    public IntOption warningDriftOption = new IntOption("warningDrift", 'w',
            "Number of instances to force a fake warning drift.", 900, 1,
            Integer.MAX_VALUE);
	
    public IntOption fakeDriftOption = new IntOption("fakeDrift", 'f',
            "Number of instances to force a fake concept drift.", 1000, 1,
            Integer.MAX_VALUE);
    
    private int num_instances;
    //ver desde el codigo de EvaluateInteractive la llamada a esto
    //agregar control de calidad, preguntar cada tanto
    //numero de errores para pasar de warning a drift
    public SimpleDetector() {
        resetLearning();
    }
    
    @Override
    public void resetLearning() {
        num_instances = 0;
    }
    
    @Override
    public void input(double prediction) {
        // prediction must be 1 or 0
        // It monitors the error rate
        if (this.isChangeDetected == true || this.isInitialized == false) {
            resetLearning();
            this.isInitialized = true;
        }
        //m_p = m_p + (prediction - m_p) / (double) m_n;
        //m_s = Math.sqrt(m_p * (1 - m_p) / (double) m_n);

        //m_n++;
        num_instances++;

        // System.out.prilnt(prediction + " " + m_n + " " + (m_p+m_s) + " ");
        //Que se asignaría acá en estimation?
        //this.estimation = m_p;
        this.isChangeDetected = false;
        this.isWarningZone = false;
        this.delay = 0;
        
        if (num_instances < fakeDriftOption.getValue()) {
            return;
        }
        
		
	if (num_instances>=fakeDriftOption.getValue()){
            //initialize();
            this.isChangeDetected = true;
            //return DDM_OUTCONTROL_LEVEL;
	} else if (num_instances>=warningDriftOption.getValue()){
            this.isWarningZone = true;
            //return DDM_WARNING_LEVEL;
	} else {
            this.isWarningZone = false;
        }
    }
    
    @Override
    public void getDescription(StringBuilder sb, int indent) {
        // TODO Auto-generated method stub
    }

    @Override
    protected void prepareForUseImpl(TaskMonitor monitor,
            ObjectRepository repository) {
        // TODO Auto-generated method stub
    }

}

