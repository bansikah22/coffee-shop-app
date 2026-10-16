ALTER TABLE customer_accounts ADD COLUMN oidc_issuer VARCHAR(512);
ALTER TABLE customer_accounts ADD COLUMN oidc_subject VARCHAR(255);
ALTER TABLE customer_accounts ADD CONSTRAINT uk_account_oidc_identity UNIQUE (oidc_issuer, oidc_subject);
ALTER TABLE customer_accounts ADD CONSTRAINT ck_account_oidc_identity
    CHECK ((oidc_issuer IS NULL AND oidc_subject IS NULL) OR
           (oidc_issuer IS NOT NULL AND oidc_subject IS NOT NULL));