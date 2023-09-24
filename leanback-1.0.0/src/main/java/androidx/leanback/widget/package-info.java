/**
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

/**
 * <p>Support classes providing low level Leanback user interface building blocks:
 * widgets and helpers.</p>
 * <p>
 * The core interface to the developer’s model is the
 * {@link androidx.leanback.widget.ObjectAdapter}. It is similar to Adapter and the
 * RecyclerView Adapter, but separates iterating items from presenting them as Views.
 * Concrete implementations include
 * {@link androidx.leanback.widget.ArrayObjectAdapter} and
 * {@link androidx.leanback.widget.CursorObjectAdapter}, but a developer is free to use a
 * subclass of an ObjectAdapter to iterate over any existing Object hierarchy.
 * </p>
 * <p>
 * A {@link androidx.leanback.widget.Presenter} creates Views and binds data from an Object
 * to those Views. This is the
 * complementary piece to ObjectAdapter that corresponds to existing Android adapter classes.
 * The benefit to separating out a Presenter is that we can use it to generate Views outside of the
 * context of an adapter. For example, a UI may represent data from a single Object in several places
 * at once. Each View that needs to be generated can be produced by a different Presenter, while the
 * Object is retrieved from the ObjectAdapter once.
 * </p>
 * A {@link androidx.leanback.widget.PresenterSelector} determines which Presenter to use
 * for a given Object from an
 * ObjectAdapter. Two common cases are when an ObjectAdapter uses the same View type for every element
 * ({@link androidx.leanback.widget.SinglePresenterSelector}), and when the Presenter is
 * determined by the Java class of
 * the element ({@link androidx.leanback.widget.ClassPresenterSelector}).  A developer is
 * able to implement any selection logic
 * as a PresenterSelector. For example, if all the elements of an ObjectAdapter have the same type,
 * but certain elements are to be rendered using a 'promotional content' view in the developer’s
 * application, the PresenterSelector may inspect the fields of each element before choosing the
 * appropriate Presenter.
 * </p>
 * <p>
 * The basic navigation model for Leanback is that of a vertical list of rows, each of which may
 * be a horizontal list of items. Therefore, Leanback uses ObjectAdapters both for defining the
 * horizontal data items as well as the list of rows themselves.
 * </p>
 * <p>Leanback defines a few basic data model classes for rows: the
 * {@link androidx.leanback.widget.Row}, which defines the
 * abstract concept of a row with a header; and {@link androidx.leanback.widget.ListRow},
 * a concrete Row implementation that uses an ObjectAdapter to present a horizontal list of items.
 * The corresponding presenter for the ListRow is the
 * {@link androidx.leanback.widget.ListRowPresenter}.
 * </p>
 * <p>
 * Other types of Rows and corresponding RowPresenters are provided; however the application may
 * define a custom subclass of {@link androidx.leanback.widget.Row} and
 * {@link androidx.leanback.widget.RowPresenter}.
 * </p>
 */

package androidx.leanback.widget;
