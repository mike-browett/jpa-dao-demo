package com.aixpertsoft.dcim.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public final class AdministratorRequests {

    private AdministratorRequests() {
    }

    public record Link(@NotNull UUID personId, boolean primaryAdministrator) {
    }

    public record Update(boolean primaryAdministrator) {
    }
}
