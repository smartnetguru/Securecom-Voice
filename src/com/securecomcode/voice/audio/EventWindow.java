/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.securecomcode.voice.audio;

import java.util.PriorityQueue;

/**
 * Tracks events that occur at specific times and provides a count of how
 * many events have occurred in a specific window of time.
 *
 * @author Stuart O. Anderson
 */
public class EventWindow {
  long windowLength;
  private PriorityQueue<Long> events = new PriorityQueue<Long>();

  EventWindow( long windowLength ) {
    this.windowLength = windowLength;
  }

  public void addEvent( long eventTime ) {
    events.add( eventTime );
  }

  public int countEvents(long now) {
    while( !events.isEmpty() && (events.peek()+windowLength < now ) ) {
      events.remove();
    }

    return events.size();
  }
}