/*
 * Copyright (C) 2014 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A ClassPresenterSelector selects a {@link Presenter} based on the item's
 * Java class.
 */
public final class ClassPresenterSelector extends PresenterSelector {

    private final ArrayList<Presenter> mPresenters = new ArrayList<Presenter>();

    private final HashMap<Class<?>, Object> mClassMap = new HashMap<Class<?>, Object>();

    /**
     * Sets a presenter to be used for the given class.
     * @param cls The data model class to be rendered.
     * @param presenter The presenter that renders the objects of the given class.
     * @return This ClassPresenterSelector object.
     */
    public ClassPresenterSelector addClassPresenter(Class<?> cls, Presenter presenter) {
        mClassMap.put(cls, presenter);
        if (!mPresenters.contains(presenter)) {
            mPresenters.add(presenter);
        }
        return this;
    }

    /**
     * Sets a presenter selector to be used for the given class.
     * @param cls The data model class to be rendered.
     * @param presenterSelector The presenter selector that finds the right presenter for a given
     *                          class.
     * @return This ClassPresenterSelector object.
     */
    public ClassPresenterSelector addClassPresenterSelector(Class<?> cls,
            PresenterSelector presenterSelector) {
        mClassMap.put(cls, presenterSelector);
        Presenter[] innerPresenters = presenterSelector.getPresenters();
        for (int i = 0; i < innerPresenters.length; i++)
        if (!mPresenters.contains(innerPresenters[i])) {
            mPresenters.add(innerPresenters[i]);
        }
        return this;
    }

    @Override
    public Presenter getPresenter(Object item) {
        Class<?> cls = item.getClass();
        Object presenter = null;

        do {
            presenter = mClassMap.get(cls);
            if (presenter instanceof PresenterSelector) {
                Presenter innerPresenter = ((PresenterSelector) presenter).getPresenter(item);
                if (innerPresenter != null) {
                    return innerPresenter;
                }
            }
            cls = cls.getSuperclass();
        } while (presenter == null && cls != null);

        return (Presenter) presenter;
    }

    @Override
    public Presenter[] getPresenters() {
        return mPresenters.toArray(new Presenter[mPresenters.size()]);
    }
}
