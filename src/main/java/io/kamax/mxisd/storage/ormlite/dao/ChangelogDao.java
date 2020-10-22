package io.kamax.mxisd.storage.ormlite.dao;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Date;

@DatabaseTable(tableName = "changelog")
public class ChangelogDao {

    @DatabaseField(id = true)
    private String id;

    @DatabaseField
    private Date createdAt;

    @DatabaseField
    private String comment;

    public ChangelogDao() {
    }

    public ChangelogDao(String id, Date createdAt, String comment) {
        this.id = id;
        this.createdAt = createdAt;
        this.comment = comment;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
