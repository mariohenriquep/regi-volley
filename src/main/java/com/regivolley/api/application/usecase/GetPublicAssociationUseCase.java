package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GetPublicAssociationQuery;
import com.regivolley.api.application.result.PublicAssociationPage;

/** US-24: a visitor reads the public page of an association. */
public interface GetPublicAssociationUseCase extends UseCase<GetPublicAssociationQuery, PublicAssociationPage> {
}
