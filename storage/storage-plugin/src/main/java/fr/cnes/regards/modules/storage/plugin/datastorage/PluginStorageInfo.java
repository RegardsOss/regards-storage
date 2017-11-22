package fr.cnes.regards.modules.storage.plugin.datastorage;

import java.util.Set;

import org.assertj.core.util.Sets;

/**
 * Contains data storage information aggregated with some meta data on this data storage: plugin id, plugin description, plugin configuration label.
 *
 * @author Sylvain VISSIERE-GUERINET
 */
public class PluginStorageInfo {

    private Long confId;

    private String description;

    private String label;

    private String totalSize;

    private String usedSize;

    private Double ratio;

    public PluginStorageInfo(Long confId, String description, String label) {
        this.confId = confId;
        this.description = description;
        this.label = label;
    }

    public Long getConfId() {
        return confId;
    }

    public void setConfId(Long confId) {
        this.confId = confId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(String totalSize) {
        this.totalSize = totalSize;
    }

    public String getUsedSize() {
        return usedSize;
    }

    public void setUsedSize(String usedSize) {
        this.usedSize = usedSize;
    }

    public Double getRatio() {
        return ratio;
    }

    public void setRatio(Double ratio) {
        this.ratio = ratio;
    }
}
