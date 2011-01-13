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

import java.util.Map;

public interface Authorizable {

  public static final String PASSWORD_FIELD = "pwd";
  public static final String PRINCIPALS_FIELD = "principals";
  public static final String MEMBERS_FIELD = "members";
  public static final String ID_FIELD = "id";
  public static final String NAME_FIELD = "name";
  public static final String AUTHORIZABLE_TYPE_FIELD = "type";
  public static final String GROUP_VALUE = "g";
  public static final Object USER_VALUE = "u";
  public static final String ADMINISTRATORS_GROUP = "administrators";
  public static final String LASTMODIFIED = "lastModified";
  public static final String LASTMODIFIED_BY = "lastModifiedBy";
  public static final String CREATED = "create";
  public static final String CREATED_BY = "createdBy";
  public static final String NO_PASSWORD = "--none--";

  public abstract String[] getPrincipals();

  public abstract String getId();

  // TODO: Unit test
  public abstract Map<String, Object> getSafeProperties();

  public abstract void setProperty(String key, Object value);

  public abstract Object getProperty(String key);

  public abstract void removeProperty(String key);

  public abstract void addPrincipal(String principal);

  public abstract void removePrincipal(String principal);

  public abstract Map<String, Object> getPropertiesForUpdate();

  public abstract void reset();

  public abstract boolean isModified();

  public abstract boolean hasProperty(String name);

}