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
package com.buddycloud.channeldirectory.search.handler.active;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.dom4j.Element;
import org.xmpp.packet.IQ;

import com.buddycloud.channeldirectory.commons.db.ChannelDirectoryDataSource;
import com.buddycloud.channeldirectory.commons.solr.SolrServerFactory;
import com.buddycloud.channeldirectory.commons.solr.SolrUtils;
import com.buddycloud.channeldirectory.search.handler.common.ChannelQueryHandler;
import com.buddycloud.channeldirectory.search.handler.response.ChannelData;
import com.buddycloud.channeldirectory.search.rsm.RSM;
import com.buddycloud.channeldirectory.search.rsm.RSMUtils;
import com.buddycloud.channeldirectory.search.utils.XMPPUtils;

/**
 * Handles queries for content metadata.
 * A query should contain a metadata query string, so
 * this handle can return channels related to this search.
 *  
 */
public class MostActiveQueryHandler extends ChannelQueryHandler {

	/**
	 * 
	 */
	private static final int DEFAULT_PAGE = 10;
	private static final int LOOK_BACK = 4; // In weeks
	private final ChannelDirectoryDataSource dataSource;

	public MostActiveQueryHandler(Properties properties, ChannelDirectoryDataSource dataSource) {
		super("http://buddycloud.com/channel_directory/most_active", properties);
		this.dataSource = dataSource;
	}

	@Override
	public IQ handle(IQ iq) {
		
		Element queryElement = iq.getElement().element("query");
		
		RSM rsm = RSMUtils.parseRSM(queryElement);
		List<String> mostActiveChannelsJids = null;
		
		try {
			mostActiveChannelsJids = retrieveMostActiveChannels(dataSource, rsm);
		} catch (SQLException e1) {
			return XMPPUtils.error(iq, "Search could not be performed, service is unavailable.", 
					getLogger());
		}
		
		List<ChannelData> channelObjects;
		try {
			channelObjects = retrieveFromSolr(mostActiveChannelsJids);
		} catch (Exception e) {
			return XMPPUtils.error(iq, "Search could not be performed, service is unavailable.", 
					getLogger());
		}
		
		return createIQResponse(iq, channelObjects, rsm);
	}

	private List<ChannelData> retrieveFromSolr(List<String> mostActiveChannelsJids) throws Exception {
		SolrServer solrServer = SolrServerFactory.createChannelCore(getProperties());
		List<ChannelData> channelsData = new LinkedList<ChannelData>();
		for (String channelJid : mostActiveChannelsJids) {
			SolrQuery solrQuery = new SolrQuery("jid:" + channelJid);
			QueryResponse queryResponse = solrServer.query(solrQuery);
			ChannelData channelData = convertResponse(queryResponse);
			if (channelData == null) {
				throw new Exception("Could not retrieve channels metadata.");
			}
			channelsData.add(channelData);
		}
		return channelsData;
	}
	
	private static ChannelData convertResponse(QueryResponse queryResponse) {
		SolrDocumentList results = queryResponse.getResults();
		if (results.isEmpty()) {
			return null;
		}
		return SolrUtils.convertToChannelData(results.iterator().next());
	}

	private static List<String> retrieveMostActiveChannels(
			ChannelDirectoryDataSource dataSource, 
			RSM rsm) throws SQLException  {
		
		Integer offset = rsm.getIndex() != null ? rsm.getIndex() : 0;
		Integer limit = rsm.getMax() != null ? rsm.getMax() : DEFAULT_PAGE;
		rsm.setCount(0);
		
		PreparedStatement statement = null;
		try {
			statement = dataSource.prepareStatement(
					"SELECT channel_jid, count(*) OVER() AS channel_count FROM channel_activity " +
					"WHERE updated > now() - interval'" + LOOK_BACK + " weeks' " +
					"ORDER BY summarized_activity DESC " +
					"LIMIT ? OFFSET ?", 
					limit, offset);
			ResultSet resultSet = statement.executeQuery();
			List<String> channelJids = new LinkedList<String>();
			String lastChannelId = null;
			while (resultSet.next()) {
				String channelJid = resultSet.getString("channel_jid");
				if (lastChannelId == null) {
					rsm.setFirst(channelJid);
				}
				lastChannelId = channelJid;
				channelJids.add(channelJid);
				rsm.setCount(resultSet.getInt("channel_count"));
			}
			rsm.setLast(lastChannelId);
			return channelJids;
		} catch (SQLException e1) {
			throw e1;
		} finally {
			ChannelDirectoryDataSource.close(statement);
		}
	}
}
