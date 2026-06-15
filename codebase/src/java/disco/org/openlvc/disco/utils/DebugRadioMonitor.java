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
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.logging.log4j.Logger;
import org.openlvc.disco.pdu.PDU;
import org.openlvc.disco.pdu.field.TransmitState;
import org.openlvc.disco.pdu.radio.SignalPdu;
import org.openlvc.disco.pdu.radio.TransmitterPdu;
import org.openlvc.disco.pdu.record.EntityId;

/**
 * A temporary debugging utility for CNR-2442.
 * </p>
 * Monitors Transmitter and Signal PDUs to identify and log when a radio unexpectedly stops
 * communicating.
 */
public class DebugRadioMonitor
{
	//----------------------------------------------------------
	//                    STATIC VARIABLES
	//----------------------------------------------------------
	public static final Duration MONITOR_TIMER_PERIOD = Duration.ofSeconds( 1 );
	public static final Duration MAX_SIGNAL_GAP = Duration.ofSeconds( 1 );
	public static final Duration HEARTBEAT_PERIOD = Duration.ofSeconds( 1 );
	public static final Duration MAX_HEARTBEAT_DELAY = Duration.ofSeconds( 2 );
	
	//----------------------------------------------------------
	//                   INSTANCE VARIABLES
	//----------------------------------------------------------
	private final Map<Integer,MonitoredRadio> radios;
	private final String name;
	private final Logger logger;
	private final long maxTxUpdateIntervalMs;
	private final long maxSignalIntervalMs;
	private Timer monitorTimer;
	
	//----------------------------------------------------------
	//                      CONSTRUCTORS
	//----------------------------------------------------------
	public DebugRadioMonitor( String name, Logger logger )
	{
		this.radios = new HashMap<>();
		this.name = name;
		this.logger = logger;
		
		this.maxTxUpdateIntervalMs = HEARTBEAT_PERIOD.toMillis() + MAX_HEARTBEAT_DELAY.toMillis();
		this.maxSignalIntervalMs = MAX_SIGNAL_GAP.toMillis();
	}
	
	//----------------------------------------------------------
	//                    INSTANCE METHODS
	//----------------------------------------------------------
	public void start()
	{
		this.monitorTimer = new Timer( this.getClass().getSimpleName()+" "+name );
		this.monitorTimer.scheduleAtFixedRate(new TimerTask()
		{
			@Override
			public void run()
			{
				checkRadios();
			}
		}, MONITOR_TIMER_PERIOD.toMillis(), MONITOR_TIMER_PERIOD.toMillis() );
	}
	
	public synchronized void stop()
	{
		if( this.monitorTimer != null )
		{
			this.monitorTimer.cancel();
			this.monitorTimer = null;
			this.radios.clear();
		}
	}
	
	public synchronized void onPdu( PDU pdu )
	{
		if( pdu instanceof TransmitterPdu tpdu )
		{
			int id = tpdu.getRadioID();
			MonitoredRadio radio = this.radios.get( id );
			if( radio != null )
				radio.onPdu( tpdu );
			else
				this.radios.put( id, new MonitoredRadio(id, tpdu.getEntityId()) );
		}
		else if( pdu instanceof SignalPdu spdu )
		{
			int id = spdu.getRadioID();
			MonitoredRadio radio = this.radios.get( id );
			if( radio != null )
				radio.onPdu( spdu );
		}
	}
	
	/**
	 * Checks the state of all monitored radios and, for any that is not as expected, logs an
	 * informative message.
	 */
	private synchronized void checkRadios()
	{
		long timeNow = System.currentTimeMillis();
		for( MonitoredRadio radio : this.radios.values() )
		{
			if( radio.isOn() )
			{
				long timeSinceLastTransmitState = timeNow - radio.lastTransmitStateTime;
				long timeSinceLastTransmissionStart = timeNow - radio.lastTransmissionStartTime;
				if( timeSinceLastTransmitState > this.maxTxUpdateIntervalMs )
				{
					logger.warn( "Radio %d (%d-%d-%d) was reported as %s %.2fs ago, but no Transmitter PDU has been received since then",
					             radio.id,
							     radio.entityId.getSiteId(),
							     radio.entityId.getAppId(),
							     radio.entityId.getEntityId(),
					             radio.lastTransmitState.name(),
					             (float)timeSinceLastTransmitState / 1000 );
					// reset the radio's state to avoid repeating logs
					radio.lastTransmitState = null;
				}
				else if( radio.isTransmitting() &&
						 timeSinceLastTransmissionStart > this.maxSignalIntervalMs )
				{
					long timeSinceLastSignal = timeNow - radio.lastSignalTime;
					if( timeSinceLastSignal > this.maxSignalIntervalMs)
					{
						logger.warn( "Radio %d (%d-%d-%d) was reported as %s %.2fs ago, but no Signal PDU has been received in the last %.2fs",
						             radio.id,
									 radio.entityId.getSiteId(),
									 radio.entityId.getAppId(),
									 radio.entityId.getEntityId(),
						             radio.lastTransmitState.name(),
						             (float)timeSinceLastTransmitState / 1000,
						             (float)timeSinceLastSignal / 1000 );
					}
				}
			}
		}
	}

	//==========================================================================================
	//----------------------------- Accessor and Mutator Methods -------------------------------
	//==========================================================================================
	
	//----------------------------------------------------------
	//                     STATIC METHODS
	//----------------------------------------------------------

	//==========================================================================================
	// Private Inner Class: MonitoredRadio   ///////////////////////////////////////////////////
	//==========================================================================================
	private static class MonitoredRadio
	{
		private final int id;
		private final EntityId entityId;
		
		private TransmitState lastTransmitState;
		private long lastTransmitStateTime;
		private long lastTransmissionStartTime;
		private long lastSignalTime;
		
		public MonitoredRadio( int id, EntityId entityId )
		{
			this.id = id;
			this.entityId = entityId;
		}
		
		public void onPdu( TransmitterPdu pdu )
		{
			if( pdu.getRadioID() != this.id )
				return;
			
			boolean wasTransmitting = this.isTransmitting();
			
			this.lastTransmitState = pdu.getTransmitState();
			this.lastTransmitStateTime = System.currentTimeMillis();
			
			if( this.isTransmitting() && !wasTransmitting )
				// new transmission started
				this.lastTransmissionStartTime = System.currentTimeMillis();
		}
		
		public void onPdu( SignalPdu pdu )
		{
			if( pdu.getRadioID() != this.id )
				return;
			
			this.lastSignalTime = System.currentTimeMillis();
		}
		
		public boolean isOn()
		{
			return this.lastTransmitState == TransmitState.OnAndTransmitting ||
			       this.lastTransmitState == TransmitState.OnButNotTransmitting;
		}
		
		public boolean isTransmitting()
		{
			return this.lastTransmitState == TransmitState.OnAndTransmitting;
		}
	}
}
