package uz.familymens.store.model;

import jakarta.persistence.*;

/** Admin paneldan o'zgartiriladigan sayt sozlamasi (matn yoki rasm havolasi). */
@Entity
@Table(name = "site_settings")
public class SiteSetting {

    @Id
    @Column(name = "setting_key", length = 80)
    private String key;

    @Column(name = "setting_value", length = 2000)
    private String value;

    protected SiteSetting() {
    }

    public SiteSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
}
