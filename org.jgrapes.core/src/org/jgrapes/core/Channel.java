/*
 * JGrapes Event Driven Framework
 * Copyright (C) 2016  Michael N. Lipp
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package org.jgrapes.core;

/**
 * Classes that implements this interface can be used as a communication bus 
 * for sending events between components.
 * <P>
 * For ordinary usage, the implementing classes {@link ClassChannel}
 * and {@link NamedChannel} should be sufficient. If another type of
 * <code>Channel</code> is needed, its implementation of this interface 
 * must make sure that {@link Eligible#isEligibleFor(Object)} returns
 * <code>true</code> if called with <code>Channel.class</code>
 * as parameter, else channels of the new type will not participate
 * in broadcasts.
 * <P>
 * Objects of type <code>Channel</code> must be immutable.
 * 
 * @see Channel#BROADCAST
 */
public interface Channel extends Eligible {

	/**
	 * A special channel object that can be passed to the constructor
	 * of {@link Component#Component(Channel)}.
	 * 
	 * @see Component#Component(Channel)
	 */
	public static final Channel SELF = new ClassChannel();
	
	/**
	 * A special channel that can be used to send events to
	 * all components.
	 */
	public static final Channel BROADCAST = new ClassChannel() {

		/**
		 * Returns <code>Channel.class</code>, the value that must
		 * by definition be matched by any channel.
		 * 
		 * @return <code>Channel.class</code>
		 */
		@Override
		public Object defaultCriterion() {
			return Channel.class;
		}

		/**
		 * Always returns {@code true} because the broadcast channel
		 * is matched by every channel.
		 * 
		 * @return {@code true}
		 */
		@Override
		public boolean isEligibleFor(Object criterion) {
			return true;
		}

		/* (non-Javadoc)
		 * @see org.jgrapes.core.ClassChannel#toString()
		 */
		@Override
		public String toString() {
			return "BROADCAST";
		}
	};

	/**
	 * Returns a textual representation of a channel's criterion.
	 * 
	 * @param criterion the criterion
	 * @return the representation
	 */
	static String criterionToString(Object criterion) {
		StringBuilder builder = new StringBuilder();
		if (criterion instanceof Class) {
			if (criterion == Channel.class) {
				builder.append("BROADCAST");
			} else {
				builder.append(Components.classToString((Class<?>) criterion));
			}
		} else {
			builder.append(criterion);
		}
		return builder.toString();
	}

	/**
	 * Returns a textual representation of a channel.
	 * 
	 * @param channel the channel
	 * @return the representation
	 */
	static String toString(Channel channel) {
		StringBuilder builder = new StringBuilder();
		if ((channel instanceof ClassChannel)
		        || (channel instanceof NamedChannel)) {
			builder.append(criterionToString(channel.defaultCriterion()));
		} else if (channel == channel.defaultCriterion()) {
			builder.append(Components.objectName(channel));
		} else {
			builder.append(channel.toString());
		}
		return builder.toString();
	}

	/**
	 * Returns a textual representation of an array of channels.
	 * 
	 * @param channels the channels
	 * @return the representation
	 */
	static String toString(Channel[] channels) {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		boolean first = true;
		for (Channel c: channels) {
			if (!first) {
				builder.append(", ");
			}
			builder.append(Channel.toString(c));
			first = false;
		}
		builder.append("]");
		return builder.toString();
	}

}
