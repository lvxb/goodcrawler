/**
 * ##########################  GoodCrawler  ############################
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sbs.goodcrawler.schedule;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.sbs.goodcrawler.exception.QueueException;
import org.sbs.goodcrawler.plugin.EsClient;
import org.sbs.pendingqueue.PendingManager;
import org.sbs.url.WebURL;

/**
 * @author shenbaise(shenbaise1001@126.com)
 * 从重爬索引库中加载到重爬队列进行爬去。---定时加载
 */
public class ReCraw {
	public void reCrawUrl(){
		try {
			SearchResponse q = EsClient.getClient().prepareSearchScroll("update").execute().get();
			System.out.println(q.toString());
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}
	
	
	public void scanUpdateUrl(){
		Client client = EsClient.getClient();
		SearchResponse scrollResp = client.prepareSearch("update")
		        .setSearchType(SearchType.SCAN)
		        .setScroll(new TimeValue(60000))
		        .setQuery(QueryBuilders.matchAllQuery())
		        .setSize(100).execute().actionGet(); //100 hits per shard will be returned for each scroll
		//Scroll until no hits are returned
		while (true) {
		    scrollResp = client.prepareSearchScroll(scrollResp.getScrollId()).setScroll(new TimeValue(600000)).execute().actionGet();
		    for (SearchHit hit : scrollResp.getHits()) {
		        //Handle the hit...
		    	hit.fields();
		    	Map<String, Object> m = hit.getSource();
		    	WebURL webURL = new WebURL();
		    	webURL.setURL((String) m.get("url"));
		    	String jobName = (String)m.get("jobName");
		    	webURL.setJobName(jobName);
		    	webURL.setRecraw(true);
		    	try {
					PendingManager.getUrlsToRecraw(jobName).addElement(webURL);
				} catch (QueueException e) {
					e.printStackTrace();
				};
		    }
		    //Break condition: No hits are returned
		    if (scrollResp.getHits().getHits().length == 0) {
		        break;
		    }
		}
	}
	public static void main(String[] args) {
		
		ReCraw craw = new ReCraw();
		craw.scanUpdateUrl();
	}
}
