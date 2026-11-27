-- Price type (admission/rental) is separate from the normal/discount basis.
ALTER TABLE place_price
    ADD COLUMN price_basis VARCHAR(20) NOT NULL DEFAULT 'NORMAL' AFTER price_kind,
    ADD CONSTRAINT ck_price_basis CHECK (price_basis IN ('NORMAL','DISCOUNT','COUPON','AFFILIATE')),
    ADD CONSTRAINT ck_verified_price_value CHECK (
        verification_status <> 'VERIFIED' OR amount_krw IS NOT NULL OR free_of_charge = TRUE
    ),
    ADD CONSTRAINT ck_verified_season_dates CHECK (
        verification_status <> 'VERIFIED' OR season_label IS NULL OR
        (valid_from IS NOT NULL AND valid_until IS NOT NULL)
    );
