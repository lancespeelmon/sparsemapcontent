/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.lite.jdbc.derby;

import com.google.common.collect.ImmutableMap;

import org.junit.Assert;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.lite.LoggingStorageListener;
import org.sakaiproject.nakamura.lite.accesscontrol.AccessControlManagerImpl;
import org.sakaiproject.nakamura.lite.accesscontrol.AuthenticatorImpl;
import org.sakaiproject.nakamura.lite.content.AbstractContentManagerTest;
import org.sakaiproject.nakamura.lite.content.ContentManagerImpl;
import org.sakaiproject.nakamura.lite.storage.StorageClientPool;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

public class ContentManagerManagerImplTest extends AbstractContentManagerTest {

    @Override
    protected StorageClientPool getClientPool() throws ClassNotFoundException {
        return DerbySetup.getClientPool();
    }

  @Test
  public void testMultiValuedIndexSearch() throws StorageClientException,
      AccessDeniedException {
    AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration);
    User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

    AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
        currentUser, configuration, null, new LoggingStorageListener(),
        principalValidatorResolver);

    ContentManagerImpl contentManager = new ContentManagerImpl(client,
        accessControlManager, configuration, null, new LoggingStorageListener());
    final String propKey = "prop1";
    final String[] multiValuedProperty = new String[] { "valueX", "valueY" };
    contentManager.update(new Content("/test", ImmutableMap.of(propKey,
        (Object) multiValuedProperty)));

    Content content = contentManager.get("/test");
    Assert.assertEquals("/test", content.getPath());
    Map<String, Object> p = content.getProperties();
    Arrays.equals(multiValuedProperty, (String[]) p.get(propKey));
    Assert.assertTrue(Arrays.equals(multiValuedProperty, (String[]) p.get(propKey)));
    // now test index search
    Map<String, Object> searchCriteria = ImmutableMap.of(propKey,
        (Object) multiValuedProperty[0]);
    Iterable<Content> iterable = contentManager.find(searchCriteria);
    Assert.assertNotNull(iterable);
    Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull(iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    Content c = iter.next();
    Assert.assertNotNull(c);
    Assert.assertNotNull(c.getProperty(propKey));
    // may need to adapt if array comes back out of order
    Assert.assertTrue(Arrays.equals(multiValuedProperty,
        (String[]) c.getProperty(propKey)));
    searchCriteria = ImmutableMap.of(propKey, (Object) multiValuedProperty[1]);
    iterable = contentManager.find(searchCriteria);
    Assert.assertNotNull(iterable);
    iter = iterable.iterator();
    Assert.assertNotNull(iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    c = iter.next();
    Assert.assertNotNull(c);
    Assert.assertNotNull(c.getProperty(propKey));
    Assert.assertTrue(Arrays.equals(multiValuedProperty, (String[]) iter.next()
        .getProperty(propKey)));
  }
}
