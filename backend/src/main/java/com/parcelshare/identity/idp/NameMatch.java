package com.parcelshare.identity.idp;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether the name on a user's ParcelShare account is the same person as the name the identity provider
 * verified. Forgiving about case, accents, punctuation and middle names ("Rahul Verma" matches "RAHUL KUMAR VERMA"),
 * strict about everything else. A mismatch is not an error: the account simply goes to an admin for review.
 */
public final class NameMatch {

    private NameMatch() {
    }

    public static boolean matches(String accountName, String verifiedName) {
        List<String> a = tokens(accountName);
        List<String> b = tokens(verifiedName);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        List<String> shorter = a.size() <= b.size() ? a : b;
        List<String> longer = a.size() <= b.size() ? b : a;
        if (shorter.size() < 2) {
            return shorter.equals(longer); // a single name is too weak to match anything but itself
        }
        return longer.containsAll(shorter);
    }

    static List<String> tokens(String name) {
        if (name == null) {
            return List.of();
        }
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String t : plain.split("[^a-z]+")) {
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
