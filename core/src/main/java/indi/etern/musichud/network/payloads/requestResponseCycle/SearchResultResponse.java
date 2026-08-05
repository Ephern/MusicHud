package indi.etern.musichud.network.payloads.requestResponseCycle;

import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.network.payloads.ApiResponsePayload;
import lombok.Getter;

import java.util.List;

@Getter
public abstract class SearchResultResponse extends ApiResponsePayload {
    private final int offset;

    protected SearchResultResponse(int offset) {
        this.offset = offset;
    }

    public abstract SearchType getSearchType();

    public abstract List<?> getResult();
}
