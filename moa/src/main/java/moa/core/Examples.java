/*
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	        http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.  
 */
package moa.core;

import com.yahoo.labs.samoa.instances.ArffLoader;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import moa.core.Utils;
import com.yahoo.labs.samoa.instances.InstanceInformation;
import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.MultiTargetArffLoader;
import com.yahoo.labs.samoa.instances.Range;

/**
 * The Class Examaples.
 *
 * @author abifet
 */
public class Examples implements Serializable, Iterable<Example> {

    /**
     * The keyword used to denote the start of an arff header
     */
    public final static String ARFF_RELATION = "@relation";

    /**
     * The keyword used to denote the start of the arff data section
     */
    public final static String ARFF_DATA = "@data";

    private static final long serialVersionUID = 8110510475535581577L;
    /**
     * The instance information.
     */
    public InstanceInformation instanceInformation;
    /**
     * The instances.
     */
    protected List<Example> examples;

    /**
     * The arff.
     */
    public ArffLoader arff;

    /**
     * A Hash that stores the indices of features.
     */
    protected HashMap<String, Integer> hsAttributesIndices;

    /**
     * Instantiates a new instances.
     *
     * @param chunk the chunk
     */
    public Examples(Examples chunk) {
        this(chunk, chunk.numExamples());
        chunk.copyExamples(0, this, chunk.numExamples());
        this.computeAttributesIndices();
    }

    /**
     * Instantiates a new instances.
     */
    public Examples() {
    }

    /**
     * Instantiates a new instances.
     *
     * @param reader the reader
     * @param size the size
     * @param classAttribute the class attribute
     */
    public Examples(Reader reader, int size, int classAttribute) {
        arff = new ArffLoader(reader, 0, classAttribute);
        this.instanceInformation = arff.getStructure();
        this.examples = new ArrayList<Example>();
        this.computeAttributesIndices();
    }

    /**
     * Instantiates a new instances.
     *
     * @param reader the reader
     * @param range
     */
    public Examples(Reader reader, Range range) {
        this.arff = new MultiTargetArffLoader(reader, range);
        this.instanceInformation = arff.getStructure();
        this.examples = new ArrayList<Example>();
        this.computeAttributesIndices();
    }

    /**
     * Instantiates a new instances.
     *
     * @param chunk the chunk
     * @param capacity the capacity
     */
    public Examples(Examples chunk, int capacity) {
        this.instanceInformation = chunk.instanceInformation();
        if (capacity < 0) {
            capacity = 0;
        }
        this.examples = new ArrayList<Example>(capacity);
        this.computeAttributesIndices();
    }

    /**
     * Instantiates a new instances.
     *
     * @param st the st
     * @param v the v
     * @param capacity the capacity
     */
    public Examples(String st, List<Attribute> v, int capacity) {
        this.instanceInformation = new InstanceInformation(st, v);
        this.examples = new ArrayList<Example>(capacity);
        this.computeAttributesIndices();
    }

    /**
     * Instantiates a new instances.
     *
     * @param chunk the chunk
     * @param first the first instance
     * @param toCopy the j
     */
    public Examples(Examples chunk, int first, int toCopy) {

        this(chunk, toCopy);

        if ((first < 0) || ((first + toCopy) > chunk.numExamples())) {
            throw new IllegalArgumentException("Parameters first and/or toCopy out "
                    + "of range");
        }
        chunk.copyExamples(first, this, toCopy);
        this.computeAttributesIndices();
    }

    /**
     * Instantiates a new instances.
     *
     * @param st the st
     * @param capacity the capacity
     */
    public Examples(StringReader st, int capacity) {
        this.examples = new ArrayList<Example>(capacity);
        this.computeAttributesIndices();
    }

    //Information Instances
    /**
     * Sets the relation name.
     *
     * @param string the new relation name
     */
    public void setRelationName(String string) {
        this.instanceInformation.setRelationName(string);
    }

    /**
     * Gets the relation name.
     *
     * @return the relation name
     */
    public String getRelationName() {
        return this.instanceInformation.getRelationName();
    }

    /**
     * Class index.
     *
     * @return the int
     */
    public int classIndex() {
        return this.instanceInformation.classIndex();
    }

    /**
     * Sets the class index.
     *
     * @param classIndex the new class index
     */
    public void setClassIndex(int classIndex) {
        this.instanceInformation.setClassIndex(classIndex);
    }

    /**
     * Class attribute.
     *
     * @return the attribute
     */
    public Attribute classAttribute() {
        return this.instanceInformation.classAttribute();
    }

    /**
     * Num attributes.
     *
     * @return the int
     */
    public int numAttributes() {
        return this.instanceInformation.numAttributes();
    }

    /**
     * Attribute.
     *
     * @param w the w
     * @return the attribute
     */
    public Attribute attribute(int w) {
        return this.instanceInformation.attribute(w);
    }

    /**
     * Num classes.
     *
     * @return the int
     */
    public int numClasses() {
        return this.instanceInformation.numClasses();
    }

    /**
     * Delete attribute at.
     *
     * @param integer the integer
     */
    public void deleteAttributeAt(Integer integer) {
        this.instanceInformation.deleteAttributeAt(integer);
    }

    /**
     * Insert attribute at.
     *
     * @param attribute the attribute
     * @param i the i
     */
    public void insertAttributeAt(Attribute attribute, int i) {
        if (this.instanceInformation == null) {
            this.instanceInformation = new InstanceInformation();
        }
        this.instanceInformation.insertAttributeAt(attribute, i);
    }

    //List of Instances
    /**
     * Instance.
     *
     * @param num the num
     * @return the instance
     */
    public Example example(int num) {
        return this.examples.get(num);
    }

    /**
     * Num instances.
     *
     * @return the int
     */
    public int numExamples() {
        return this.examples.size();
    }

    /**
     * Adds the.
     *
     * @param inst the inst
     */
    public void add(Example inst) {
        this.examples.add(inst.copy());
    }

    /**
     * Randomize.
     *
     * @param random the random
     */
    public void randomize(Random random) {
        for (int j = numExamples() - 1; j > 0; j--) {
            swap(j, random.nextInt(j + 1));
        }
    }

    protected void stratStep(int numFolds) {
        ArrayList<Example> newVec = new ArrayList<Example>(this.examples.size());
        int start = 0, j;

        // create stratified batch
        while (newVec.size() < numExamples()) {
            j = start;
            while (j < numExamples()) {
                newVec.add(example(j));
                j = j + numFolds;
            }
            start++;
        }
        this.examples = newVec;
    }

    /**
     * Train cv.
     *
     * @param numFolds the num folds
     * @param numFold
     * @param n the n
     * @param random the random
     * @return the instances
     */
    public Examples trainCV(int numFolds, int numFold, Random random) {
        Examples train = trainCV(numFolds, numFold);
        train.randomize(random);
        return train;
    }

    public Examples trainCV(int numFolds, int numFold) {
        int numInstForFold, first, offset;
        Examples train;

        numInstForFold = numExamples() / numFolds;
        if (numFold < numExamples() % numFolds) {
            numInstForFold++;
            offset = numFold;
        } else {
            offset = numExamples() % numFolds;
        }
        train = new Examples(this, numExamples() - numInstForFold);
        first = numFold * (numExamples() / numFolds) + offset;
        copyExamples(0, train, first);
        copyExamples(first + numInstForFold, train,
                numExamples() - first - numInstForFold);
        return train;
    }

    protected void copyExamples(int from, Examples dest, int num) {
        for (int i = 0; i < num; i++) {
            dest.add(example(from + i));
        }
    }

    /**
     * Test cv.
     *
     * @param numFolds the num folds
     * @param numFold the num fold
     * @return the instances
     */
    public Examples testCV(int numFolds, int numFold) {

        int numInstForFold, first, offset;
        Examples test;

        numInstForFold = numExamples() / numFolds;
        if (numFold < numExamples() % numFolds) {
            numInstForFold++;
            offset = numFold;
        } else {
            offset = numExamples() % numFolds;
        }
        test = new Examples(this, numInstForFold);
        first = numFold * (numExamples() / numFolds) + offset;
        copyExamples(first, test, numInstForFold);
        return test;
    }

    /*  public Instances dataset() {
     throw new UnsupportedOperationException("Not yet implemented");
     }*/
    /**
     * Mean or mode.
     *
     * @param j the j
     * @return the double
     */
    public double meanOrMode(int j) {
        throw new UnsupportedOperationException("Not yet implemented"); //CobWeb
    }

    

    /**
     * Delete.
     */
    public void delete() {
        this.examples = new ArrayList<Example>();
    }

    /**
     * Delete.
     */
    public void delete(int index) {
        this.examples.remove(index);
    }

    /**
     * Swap.
     *
     * @param i the i
     * @param j the j
     */
    public void swap(int i, int j) {
        Example in = examples.get(i);
        examples.set(i, examples.get(j));
        examples.set(j, in);
    }

    /**
     * Instance information.
     *
     * @return the instance information
     */
    private InstanceInformation instanceInformation() {
        return this.instanceInformation;
    }

    public Attribute attribute(String name) {

        for (int i = 0; i < numAttributes(); i++) {
            if (attribute(i).name().equals(name)) {
                return attribute(i);
            }
        }
        return null;
    }

    public int size() {
        return this.numExamples();
    }

    public void set(int i, Example inst) {
        this.examples.set(i, inst);
    }

    public Example get(int k) {
        return this.example(k);
    }

    public void setRangeOutputIndices(Range range) {
        this.instanceInformation.setRangeOutputIndices(range);

    }

    public void setAttributes(List<Attribute> v) {
        if (this.instanceInformation == null) {
            this.instanceInformation = new InstanceInformation();
        }
        this.instanceInformation.setAttributes(v);
    }

    public void setAttributes(List<Attribute> v, List<Integer> indexValues) {
        if (this.instanceInformation == null) {
            this.instanceInformation = new InstanceInformation();
        }
        this.instanceInformation.setAttributes(v, indexValues);
    }

    /**
     * Returns the dataset as a string in ARFF format. Strings are quoted if
     * they contain whitespace characters, or if they are a question mark.
     *
     * @return the dataset in ARFF format as a string
     */
    public String toString() {

        StringBuffer text = new StringBuffer();

        text.append(ARFF_RELATION).append(" ").
                append(Utils.quote(this.instanceInformation.getRelationName())).append("\n\n");
        for (int i = 0; i < numAttributes(); i++) {
            text.append(attribute(i).toString()).append("\n");
        }
        text.append("\n").append(ARFF_DATA).append("\n");

        text.append(stringWithoutHeader());
        return text.toString();
    }

    /**
     * Returns the instances in the dataset as a string in ARFF format. Strings
     * are quoted if they contain whitespace characters, or if they are a
     * question mark.
     *
     * @return the dataset in ARFF format as a string
     */
    protected String stringWithoutHeader() {

        StringBuffer text = new StringBuffer();

        for (int i = 0; i < numExamples(); i++) {
            text.append(example(i));
            if (i < numExamples() - 1) {
                text.append('\n');
            }
        }
        return text.toString();

    }

    /**
     * Returns the index of an Attribute.
     *
     * @param att, the attribute.
     */
    protected int indexOf(Attribute att) {
        return this.hsAttributesIndices.get(att.name());
    }

    /**
     * Completes the hashset with attributes indices.
     */
    private void computeAttributesIndices() {
        this.hsAttributesIndices = new HashMap<String, Integer>();
        // iterates through all existing attributes 
        // and sets an unique identifier for each one of them
        for (int i = 0; i < this.numAttributes(); i++) {
            hsAttributesIndices.put(this.attribute(i).name(), i);
        }
    }

    @Override
    public Iterator iterator() {
        final int tam = this.size();
        final List<Example> arrayList = this.examples;
        Iterator<Example> it = new Iterator<Example>() {
                
            private int currentIndex = 0;

            @Override
            public boolean hasNext() {
             
                return currentIndex < tam;
            }

            @Override
            public Example next() {
                return arrayList.get(currentIndex++);
                //lreturn arrayList[currentIndex++];
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
        return it;
    }
}

