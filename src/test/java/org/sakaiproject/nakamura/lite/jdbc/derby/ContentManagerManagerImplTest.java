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
    // add some content with multi-valued properties
    final String propKey = "prop1";
    final String[] multiValueA = new String[] { "valueA", "valueB" };
    final String[] multiValueX = new String[] { "valueX", "valueY", "valueZ" };
    contentManager.update(new Content("/testA", ImmutableMap.of(propKey,
        (Object) multiValueA)));
    contentManager.update(new Content("/testX", ImmutableMap.of(propKey,
        (Object) multiValueX)));

    // verify state of content
    Content contentA = contentManager.get("/testA");
    Content contentX = contentManager.get("/testX");
    Assert.assertEquals("/testA", contentA.getPath());
    Assert.assertEquals("/testX", contentX.getPath());
    Map<String, Object> propsA = contentA.getProperties();
    Map<String, Object> propsX = contentX.getProperties();
    Assert.assertTrue(Arrays.equals(multiValueA, (String[]) propsA.get(propKey)));
    Assert.assertTrue(Arrays.equals(multiValueX, (String[]) propsX.get(propKey)));

    // now test index search; search for "a" find contentA
    Map<String, Object> searchCriteria = ImmutableMap.of(propKey,
        (Object) multiValueA);
    Map<String, Object> orSet = ImmutableMap.of("orset0", (Object) searchCriteria);
    Iterable<Content> iterable = contentManager.find(orSet);
    Assert.assertNotNull("Iterable should not be null", iterable);
    Iterator<Content> iter = iterable.iterator();
    Assert.assertNotNull("Iterator should not be null", iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    Content match = iter.next();
    Assert.assertNotNull("match should not be null", match);
    Assert.assertEquals("/testA", match.getPath());
    Assert.assertNotNull("match should have key: " + propKey, match.getProperty(propKey));
    Assert.assertTrue("String[] should be equal",
        Arrays.equals(multiValueA, (String[]) match.getProperty(propKey)));
    searchCriteria = ImmutableMap.of(propKey, (Object) multiValueA[1]);
    iterable = contentManager.find(searchCriteria);
    Assert.assertNotNull(iterable);
    iter = iterable.iterator();
    Assert.assertNotNull(iter);
    Assert.assertTrue("Should have found a match", iter.hasNext());
    match = iter.next();
    Assert.assertNotNull(match);
    Assert.assertNotNull(match.getProperty(propKey));
    Assert.assertTrue(Arrays.equals(multiValueA,
        (String[]) iter.next().getProperty(propKey)));
  }
}
