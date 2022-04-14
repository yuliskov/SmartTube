/*
 * Copyright (c) 2012-2014 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package org.spongycastle.util.io.pem;

public class PemHeader {
    private String name;
    private String value;

    public PemHeader(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public int hashCode() {
        return getHashCode(this.name) + 31 * getHashCode(this.value);
    }

    public boolean equals(Object o) {
        if (!(o instanceof PemHeader)) {
            return false;
        }
        PemHeader other = (PemHeader) o;
        return other == this || (isEqual(this.name, other.name) && isEqual(this.value, other.value));
    }

    private int getHashCode(String s) {
        if (s == null) {
            return 1;
        }
        return s.hashCode();
    }

    private boolean isEqual(String s1, String s2) {
        if (s1 == s2) {
            return true;
        }
        if (s1 == null || s2 == null) {
            return false;
        }
        return s1.equals(s2);
    }
}
