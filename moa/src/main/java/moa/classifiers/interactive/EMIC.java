/*
 *    EMIC.java
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
import moa.options.AbstractOptionHandler;

// Abstract class which defines a generic EMIC measure
// Current implementations of this class are: ExpectedProfit.java and Fbeta.java

public abstract class EMIC extends AbstractOptionHandler implements MOAObject{
	
	private static final long serialVersionUID = -1229452047454728765L;

	protected long successes;
	
	protected long fails;
	
	protected long interactions;
	
	protected long total;
        
        protected long successesLocal;
	
	protected long failsLocal;
	
	protected long interactionsLocal;
	
	protected long totalLocal;
	
	public EMIC(){
		this.successes = 0;
		this.fails = 0;
		this.interactions = 0;
		this.total = 0;
                this.successesLocal = 0;
		this.failsLocal = 0;
		this.interactionsLocal = 0;
		this.totalLocal = 0;
	}
	
	public abstract double getValue();
	
	public long getSuccesses(){
		return this.successes;
	}
	
	public void setSuccesses(long successes){
		this.successes = successes;
	}
	
	public long getFails(){
		return this.fails;
	}
	
	public void setFails(long fails){
		this.fails = fails;
	}
	
	public long getInteractions(){
		return this.interactions;
	}
	
	public void setInteractions(long interactions){
		this.interactions = interactions;
	}
	
	
	public long getTotal(){
		return this.total;
	}
	
	public void setTotal(long total){
		this.total = total;
	}
	
	public long getSuccessesLocal(){
		return this.successesLocal;
	}
	
	public void setSuccessesLocal(long successesLocal){
		this.successesLocal = successesLocal;
	}
	
	public long getFailsLocal(){
		return this.failsLocal;
	}
	
	public void setFailsLocal(long failsLocal){
		this.failsLocal = failsLocal;
	}
	
	public long getInteractionsLocal(){
		return this.interactionsLocal;
	}
	
	public void setInteractionsLocal(long interactionsLocal){
		this.interactionsLocal = interactionsLocal;
	}
	
	
	public long getTotalLocal(){
		return this.totalLocal;
	}
	
	public void setTotalLocal(long totalLocal){
		this.totalLocal = totalLocal;
	}
	
}
