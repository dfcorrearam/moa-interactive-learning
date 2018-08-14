/*
 *    MaximumProbability.java
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
import moa.core.Utils;

public class MaximumProbability implements ConfidenceMeasure {

	private static final long serialVersionUID = 1L;

	@Override
	public double getValue(double[] prediction) {
		int i = Utils.maxIndex(prediction);
//		for (int k=0; k<prediction.length; k++){
//			System.out.println("prediction["+k+"] = "+prediction[k]);
//		}
//		System.out.println("max = " + prediction[i]);
		return prediction[i];
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