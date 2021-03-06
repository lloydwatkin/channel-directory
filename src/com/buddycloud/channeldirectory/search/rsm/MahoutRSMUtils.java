/*
 * Copyright 2011 buddycloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.buddycloud.channeldirectory.search.rsm;

import java.util.List;

import com.buddycloud.channeldirectory.search.handler.common.mahout.RecommendationResponse;
import com.buddycloud.channeldirectory.search.handler.response.ChannelData;

/**
 * It is responsible for providing utility methods related to RSM format 
 * (http://xmpp.org/extensions/xep-0059.html),
 * which are used on the Solr query processing and response.
 * 
 * @see RSM
 *  
 */
public class MahoutRSMUtils {

	public static int preprocess(RSM rsm)
			throws IllegalArgumentException {
		
		String after = rsm.getAfter();
		String before = rsm.getBefore();
		
		int initialIndex = rsm.getIndex();
		int lastIndex = -1;
		
		if (after != null) {
			initialIndex = Integer.valueOf(after);
		}
		if (before != null && !before.isEmpty()) {
			lastIndex = Integer.valueOf(before) - 2;
		}
		
		if (rsm.getMax() != null) {
			if (before != null) {
				initialIndex = lastIndex - rsm.getMax() + 1;
			} else {
				lastIndex = initialIndex + rsm.getMax() - 1;
			}
		}
		
		rsm.setIndex(initialIndex);
		rsm.setFirst(String.valueOf(initialIndex + 1));
		rsm.setLast(String.valueOf(lastIndex + 1));
		
		return lastIndex + 1;
	}
	
	public static List<ChannelData> postprocess(
			RecommendationResponse response, RSM rsm)
			throws IllegalArgumentException {
		
		rsm.setCount(response.getNumFound());
		List<ChannelData> allItems = response.getResponse();
		
		Integer fromIndex = Integer.valueOf(rsm.getFirst()) - 1;
		Integer toIndex = Math.min(Integer.valueOf(rsm.getLast()), allItems.size());
		
		List<ChannelData> responseItems = allItems.subList(fromIndex, toIndex);
		
		if (responseItems.isEmpty()) {
			rsm.setFirst(null);
			rsm.setLast(null);
		} else {
			rsm.setLast(toIndex.toString());
		}
		
		return responseItems;
	}
}
