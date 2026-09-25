package it.dhd.oxygencustomizer.ui.models;

import androidx.annotation.StringRes;
import androidx.annotation.XmlRes;
import androidx.fragment.app.Fragment;

public class SearchPreferenceItem {
    @XmlRes
    private final int xml;

    @StringRes
    private final int title;

    private final Class<? extends Fragment> fragmentClass;
    private final boolean shouldAdd;

    public SearchPreferenceItem(
            @XmlRes int xml,
            @StringRes int title,
            Class<? extends Fragment> fragmentClass) {
        this(xml, title, fragmentClass, true);
    }

    public SearchPreferenceItem(
            @XmlRes int xml,
            @StringRes int title,
            Class<? extends Fragment> fragmentClass,
            boolean shouldAdd) {
        this.xml = xml;
        this.title = title;
        this.fragmentClass = fragmentClass;
        this.shouldAdd = shouldAdd;
    }

    public int getXml() {
        return xml;
    }

    public int getTitle() {
        return title;
    }

    public Fragment createFragment() {
        try {
            return fragmentClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Unable to instantiate search fragment " + fragmentClass.getName(),
                    e
            );
        }
    }

    public boolean shouldAdd() {
        return shouldAdd;
    }
}
