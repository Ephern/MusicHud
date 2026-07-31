package indi.etern.musichud.beans.music.actions;

import lombok.Getter;

public enum ModifyType {
    ADD("add"), REMOVE("del");

    @Getter
    private final String apiOperationName;

    @Override
    public String toString() {
        return apiOperationName;
    }

    ModifyType(String apiOperationName) {
        this.apiOperationName = apiOperationName;
    }
}