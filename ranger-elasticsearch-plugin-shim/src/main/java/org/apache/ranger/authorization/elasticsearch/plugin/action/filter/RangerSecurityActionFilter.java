/*
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

package org.apache.ranger.authorization.elasticsearch.plugin.action.filter;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.ranger.authorization.elasticsearch.authorizer.RangerElasticsearchAuthorizer;
import org.apache.ranger.authorization.elasticsearch.plugin.authc.user.UsernamePasswordToken;
import org.apache.ranger.authorization.elasticsearch.plugin.utils.RequestUtils;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.action.support.ActionFilterChain;
//import org.elasticsearch.common.component.AbstractComponent;
//import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.tasks.Task;

public class RangerSecurityActionFilter implements ActionFilter {

	private static final Logger LOG = Loggers.getLogger(RangerSecurityActionFilter.class);

	private final ThreadContext threadContext;

	private final Settings settings;

	private final RangerElasticsearchAuthorizer rangerElasticsearchAuthorizer = new RangerElasticsearchAuthorizer();

	public RangerSecurityActionFilter(Settings settings, ThreadContext threadContext) {
		this.settings = settings;
		this.threadContext = threadContext;
	}

	@Override
	public int order() {
		return 0;
	}

	@Override
	public <Request extends ActionRequest, Response extends ActionResponse> void apply(Task task, String action,
			Request request, ActionListener<Response> listener, ActionFilterChain<Request, Response> chain) {
		String user = threadContext.getTransient(UsernamePasswordToken.USERNAME);
		// If user is not null, then should check permission of the outside caller.
		if (StringUtils.isNotEmpty(user)) {
			List<String> indexs = RequestUtils.getIndexFromRequest(request);
			String clientIPAddress = threadContext.getTransient(RequestUtils.CLIENT_IP_ADDRESS);
			for (String index : indexs) {
				boolean result = rangerElasticsearchAuthorizer.checkPermission(user, null, index, action,
						clientIPAddress);
				if (!result) {
					String errorMsg = "Error: User[{}] could not do action[{}] on index[{}]";
					throw new ElasticsearchStatusException(errorMsg, RestStatus.FORBIDDEN, user, action, index);
				}
			}
		} else {
			if (LOG.isDebugEnabled()) {
				LOG.debug("User is null, no check permission for elasticsearch do action[{}] with request[{}]", action,
						request);
			}
		}
		chain.proceed(task, action, request, listener);
	}

	public Settings getSettings() {
		return settings;
	}
}
