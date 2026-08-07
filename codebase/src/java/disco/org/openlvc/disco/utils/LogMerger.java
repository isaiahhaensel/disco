/*
 *   Copyright 2026 Open LVC Project.
 *
 *   This file is part of Open LVC Disco.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.openlvc.disco.utils;

import java.time.Duration;
import java.util.Optional;

/**
 * Provides a simplified mechanism to reduce spam of debugging or troubleshooting logs by counting
 * calls.
 */
public class LogMerger
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------
	
	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	private final long mergeTime;
	private final long mergeCalls;
	
	private boolean isFirst = false;
	private long timeOfLastRun = 0;
	private long callsSinceLastRun = 0;
	
	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
	/**
	 * @param mergeTime the maximum time over which to merge logs.
	 */
	public LogMerger( Duration mergeTime )
	{
		this( mergeTime, 0 );
	}
	
	/**
	 * @param mergeCalls the maximum number of log calls to merge.
	 */
	public LogMerger( long mergeCalls )
	{
		this( Duration.ZERO, mergeCalls );
	}
	
	/**
	 * @param mergeTime  the maximum time over which to merge logs.
	 * @param mergeCalls the maximum number of log calls to merge.
	 */
	public LogMerger( Duration mergeTime, long mergeCalls )
	{
		this.mergeTime = Math.max(0, mergeTime.toMillis());
		
		// if not merging by time, must merge by count
		if( this.mergeTime == 0 && mergeCalls <= 0 )
			mergeCalls = 1;
		this.mergeCalls = mergeCalls;
	}
	
	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------
	/**
	 * If this is the first call, returns {@code 1}. Otherwise, if the time since the last call is
	 * at least {@link #mergeTime}, or if the number of calls (including this one) since the last
	 * non-zero return is at least {@link #mergeCalls}, returns the number of calls since the last
	 * non-zero return.
	 *
	 * @return {@code 0} if the associated log call should be merged into a later call, otherwise
	 *         the number of logs calls that should be merged into the associated log call.
	 */
	public synchronized Optional<Long> getMergeCount()
	{
		long timeNow = System.currentTimeMillis();
		long timeSinceLastRun = timeNow - this.timeOfLastRun;
		
		this.callsSinceLastRun++;
		
		boolean shouldMerge = true;
		
		if( this.isFirst )
		{
			this.isFirst = false;
			shouldMerge = false;
		}
		else if( this.mergeTime > 0 && timeSinceLastRun >= this.mergeTime )
		{
			// time threshold is enabled and reached
			shouldMerge = false;
		}
		else if( this.mergeCalls > 0 && this.callsSinceLastRun >= this.mergeCalls )
		{
			// call threshold is enabled and reached
			shouldMerge = false;
		}
		
		if( !shouldMerge )
		{
			long callsMerged = this.callsSinceLastRun;
			this.callsSinceLastRun = 0;
			this.timeOfLastRun = timeNow;
			return Optional.of(callsMerged);
		}
		
		// merge
		return Optional.empty();
	}
	
	//==========================================================================================
	//----------------------------- Accessor and Mutator Methods -------------------------------
	//==========================================================================================
	
	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------
}
