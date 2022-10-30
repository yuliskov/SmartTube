/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.drm;

/**
 * A reference-counted resource used in the decryption of media samples.
 *
 * @param <T> The reference type with which to make {@link Owner#onLastReferenceReleased} calls.
 *     Subclasses are expected to pass themselves.
 */
public abstract class DecryptionResource<T extends DecryptionResource<T>> {

  /**
   * Implemented by the class in charge of managing a {@link DecryptionResource resource's}
   * lifecycle.
   */
  public interface Owner<T extends DecryptionResource<T>> {

    /**
     * Called when the last reference to a {@link DecryptionResource} is {@link #releaseReference()
     * released}.
     */
    void onLastReferenceReleased(T resource);
  }

  // TODO: Consider adding a handler on which the owner should be called.
  private final DecryptionResource.Owner<T> owner;
  private int referenceCount;

  /**
   * Creates a new instance with reference count zero.
   *
   * @param owner The owner of this instance.
   */
  public DecryptionResource(Owner<T> owner) {
    this.owner = owner;
    referenceCount = 0;
  }

  /** Increases by one the reference count for this resource. */
  public void acquireReference() {
    referenceCount++;
  }

  /**
   * Decreases by one the reference count for this resource, and notifies the owner if said count
   * reached zero as a result of this operation.
   *
   * <p>Must only be called as releasing counter-part of {@link #acquireReference()}.
   */
  @SuppressWarnings("unchecked")
  public void releaseReference() {
    if (--referenceCount == 0) {
      owner.onLastReferenceReleased((T) this);
    } else if (referenceCount < 0) {
      throw new IllegalStateException("Illegal release of resource.");
    }
  }
}
