/*******************************************************************************
 * Copyright 2016 stfalcon.com
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package com.stfalcon.chatkit.commons.models;

/**
 * For implementing by real user model
 */
public interface IUser {

    /**
     * Returns the user's id
     *
     * @return the user's id
     */
    String getId();

    /**
     * Returns the user's name
     *
     * @return the user's name
     */
    String getName();

    /**
     * Returns the user's avatar image url
     *
     * @return the user's avatar image url
     */
    String getAvatar();
}
