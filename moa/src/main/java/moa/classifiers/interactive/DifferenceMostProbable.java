/*
 *    DifferenceMostProbable.java
 *    Copyright (C) 2011 Universidad de Granada, Granada, Spain
 *    @author Manuel Martín (manuelmartin@decsai.ugr.es)
 *
 *    This program is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program; if not, write to the Free Software
 *    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package moa.classifiers.interactive;

import moa.MOAObject;
import weka.core.Utils;

public class DifferenceMostProbable implements ConfidenceMeasure {

	private static final long serialVersionUID = 1L;

	@Override
	public double getValue(double[] prediction) {
		// Get vector indexes sorted from lowest to highest
		int[] i = Utils.sort(prediction);
		// Return the difference between the two most probable classes
		return prediction[i.length-1]-prediction[i.length-2]; 
	}

	@Override
	public int measureByteSize() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public MOAObject copy() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void getDescription(StringBuilder sb, int indent) {
		// TODO Auto-generated method stub
		
	}
	
}