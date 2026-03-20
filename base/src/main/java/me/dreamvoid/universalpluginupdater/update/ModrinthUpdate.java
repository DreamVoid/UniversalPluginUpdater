package me.dreamvoid.universalpluginupdater.update;

public class ModrinthUpdate extends AbstractUpdate {
    public String projectId;
    public String versionId;

    public ModrinthUpdate(String projectId, String versionId) {
        this.updateType = UpdateType.Modrinth;
        this.projectId = projectId;
        this.versionId = versionId;
    }

    @Override
    public void download() {

    }
}
