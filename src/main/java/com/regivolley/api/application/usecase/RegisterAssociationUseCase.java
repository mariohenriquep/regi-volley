package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RegisterAssociationCommand;
import com.regivolley.api.application.result.AssociationRegistered;

/** US-01: a visitor registers an association and becomes its administrator. */
public interface RegisterAssociationUseCase extends UseCase<RegisterAssociationCommand, AssociationRegistered> {
}
