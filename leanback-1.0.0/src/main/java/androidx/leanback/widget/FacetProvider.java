/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package androidx.leanback.widget;

/**
 * This is the query interface to supply optional features(aka facets) on an object without the need
 * of letting the object to subclass or implement java interfaces.
 */
public interface FacetProvider {

    /**
     * Queries optional implemented facet.
     * @param facetClass  Facet classes to query,  examples are: class of
     *                    {@link ItemAlignmentFacet}.
     * @return Facet implementation for the facetClass or null if feature not implemented.
     */
    public Object getFacet(Class<?> facetClass);

}
