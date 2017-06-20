/*
 *  (c) 2014 Adobe. All rights reserved.
 *  This file is licensed to you under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License. You may obtain a copy
 *  of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 *  OF ANY KIND, either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License.
 */
package com.adobe.cq.dialogconversion;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Dictionary;

/**
 * Base class for services that implement a dialog rewrite rule. This class provides a default implementation of
 * {@link DialogRewriteRule#getRanking} using the <code>service.ranking</code> OSGi property.
 */
@Component(componentAbstract = true)
public abstract class AbstractDialogRewriteRule implements DialogRewriteRule {

    private Logger logger = LoggerFactory.getLogger(AbstractDialogRewriteRule.class);

    private int ranking = Integer.MAX_VALUE;

    @Activate
    protected void activate(ComponentContext context)
            throws RepositoryException {
        @SuppressWarnings("unchecked")
        Dictionary<String, Object> props = context.getProperties();
        // read service ranking property
        Object ranking = props.get("service.ranking");
        if (ranking != null) {
            try {
                this.ranking = (Integer) ranking;
            } catch (ClassCastException e) {
                // ignore
                logger.warn("Could invalid service.ranking value {}", ranking);
            }
        }
    }

    public int getRanking() {
        return ranking;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[ranking=" + getRanking() + "]";
    }

}
