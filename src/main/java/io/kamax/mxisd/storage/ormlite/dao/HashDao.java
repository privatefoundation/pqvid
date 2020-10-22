package io.kamax.mxisd.storage.ormlite.dao;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "hashes")
public class HashDao {

    @DatabaseField(generatedId = true)
    private Long id;

    @DatabaseField(canBeNull = false, uniqueCombo = true)
    private String mxid;

    @DatabaseField(canBeNull = false, uniqueCombo = true)
    private String medium;

    @DatabaseField(canBeNull = false, uniqueCombo = true)
    private String address;

    @DatabaseField(canBeNull = false)
    private String hash;

    public HashDao() {
    }

    public HashDao(String mxid, String medium, String address, String hash) {
        this.mxid = mxid;
        this.medium = medium;
        this.address = address;
        this.hash = hash;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMxid() {
        return mxid;
    }

    public void setMxid(String mxid) {
        this.mxid = mxid;
    }

    public String getMedium() {
        return medium;
    }

    public void setMedium(String medium) {
        this.medium = medium;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
