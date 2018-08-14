/*
 *    Fbeta.java
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

import moa.core.ObjectRepository;
import com.github.javacliparser.FloatOption;
import moa.tasks.TaskMonitor;

public class Fbeta extends EMIC {

	private static final long serialVersionUID = -3442013938176854399L;
	
	public FloatOption betaOption = new FloatOption("beta", 'b', "beta parameter", 0.5, 0, Double.MAX_VALUE);

	@Override
	public int measureByteSize() {
		return 0;
	}

	@Override
	public void getDescription(StringBuilder sb, int indent) {
		// TODO Auto-generated method stub

	}

	@Override
	public double getValue() {
		double beta = betaOption.getValue();
		return ((1+beta*beta)*this.successes) / ((1+beta*beta)*this.total - this.interactions);
	}

	@Override
	protected void prepareForUseImpl(TaskMonitor monitor,
			ObjectRepository repository) {
		// TODO Auto-generated method stub
		
	}

}
