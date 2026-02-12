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

import androidx.annotation.Nullable;

import com.stfalcon.chatkit.messages.MessageHolders;

/*
 * Created by troy379 on 28.03.17.
 */

/**
 * Interface used to mark messages as custom content types. For its representation see {@link MessageHolders}
 */

public interface MessageContentType extends IMessage {

    /**
     * Default media type for image message.
     */
    interface Image extends IMessage {
        @Nullable
        String getImageUrl();
    }

    // other default types will be here

}
