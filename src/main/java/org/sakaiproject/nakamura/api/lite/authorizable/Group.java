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
package org.sakaiproject.nakamura.api.lite.authorizable;

import com.google.common.collect.ImmutableMap;

import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.lite.authorizable.GroupImpl;

import java.util.Map;

public interface Group extends Authorizable {

  /**
   * The ID of the everyone group. Includes all users except anon.
   */
  public static final String EVERYONE = "everyone";

  public static final Group EVERYONE_GROUP = new GroupImpl(ImmutableMap.of("id",
      StorageClientUtils.toStore(EVERYONE)));

  public abstract Map<String, Object> getPropertiesForUpdate();

  public abstract Map<String, Object> getSafeProperties();

  public abstract boolean isModified();

  public abstract String[] getMembers();

  public abstract void addMember(String member);

  public abstract void removeMember(String member);

  public abstract String[] getMembersAdded();

  public abstract String[] getMembersRemoved();

  public abstract void reset();

}