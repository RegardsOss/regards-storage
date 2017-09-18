package fr.cnes.regards.modules.storage.domain.database;

import java.net.URL;
import java.time.OffsetDateTime;

/**
 * @author Sylvain VISSIERE-GUERINET
 */
public class CachedFile {

    private Long id;

    private String checksum;

    private URL location;

    private OffsetDateTime expiration;

    private CachedFileState state;

    private String failureCause;

    public CachedFile() {
    }

    public CachedFile(DataFile df, OffsetDateTime expiration) {
        this.checksum = df.getChecksum();
        this.expiration = expiration;
        this.state = CachedFileState.RESTORING;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public URL getLocation() {
        return location;
    }

    public void setLocation(URL location) {
        this.location = location;
    }

    public OffsetDateTime getExpiration() {
        return expiration;
    }

    public void setExpiration(OffsetDateTime expiration) {
        this.expiration = expiration;
    }

    public CachedFileState getState() {
        return state;
    }

    public void setState(CachedFileState state) {
        this.state = state;
    }

    public void setFailureCause(String failureCause) {
        this.failureCause = failureCause;
    }

    public String getFailureCause() {
        return failureCause;
    }
}
