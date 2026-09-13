# Wave Transakt × Interswitch SmartPOS onboarding

This document is the provider handoff specification for connecting Wave Transakt's already-built merchant/POS foundation to genuine Interswitch merchant acquiring, SmartPOS, contactless acceptance, settlement/reconciliation, and card issuing.

## Current Wave state

Wave already has:
- merchant records and persistent merchant QR identities;
- Wave POS terminal records and owner-controlled pairing;
- revocable terminal sessions using `X-Wave-POS-Session`;
- provider-neutral acquiring interfaces;
- fail-closed Interswitch acquiring gateway;
- provider-event verification and deduplication boundary;
- a terminal-provisioning orchestrator;
- separate QR, card, and contactless capability flags;
- provider-backed transaction, receipt, settlement, and reconciliation read models.

Wave does **not** invent MID/TID values, provider approvals, settlement status, contactless capability, or card issuance.

## Provider information required before activation

### 1. Merchant onboarding / acquiring
Please provide the approved onboarding path for Wave Transakt merchants, including:
- UAT/sandbox merchant registration process;
- assigned Wave/acquirer merchant identifier or merchant code;
- MID format and lifecycle;
- business/KYC/KYB documents required;
- merchant activation and suspension states;
- supported MCC/business categories;
- production go-live approval process.

### 2. SmartPOS terminal provisioning
Please provide:
- supported terminal model recommended for our first controlled pilot;
- supported PAX, Kozen, and/or Telpo model numbers;
- UAT/test terminal availability and purchase/loan process;
- terminal serial-number registration process;
- TID allocation format and lifecycle;
- relationship among merchant code, MID, TID, and physical serial number;
- terminal call-home / remote provisioning requirements;
- terminal replacement, retirement, and re-link process;
- whether one MID may have multiple TIDs.

Wave's backend expects a successful provider-link result to include, at minimum:
- provider code;
- real provider terminal ID/TID;
- explicit card-acceptance grant;
- explicit contactless/NFC grant.

The Wave app must never be able to fabricate these values locally.

### 3. SmartPOS SDK and credential model
Current public Interswitch SmartPOS documentation describes `smart-pos-core` and device-specific PAX/Kozen/Telpo modules.

Please confirm for Wave:
- approved SDK version;
- Maven/repository coordinates or distribution method;
- required device vendor library versions;
- UAT and production environment values;
- the approved credential model for a deployed merchant terminal;
- whether `clientId`, `clientSecret`, alias, merchantCode, or other credentials are device-specific, merchant-specific, application-specific, or backend-issued;
- credential rotation and revocation process;
- whether any secret may be embedded in an APK (Wave's policy is **no backend OAuth/client secret in the Android/POS APK**);
- terminal/app attestation or certificate requirements.

### 4. Card and contactless acceptance
Please explicitly confirm independent enablement for:
- chip/EMV card acceptance;
- magstripe fallback if applicable;
- PIN flow and supported PIN-entry hardware;
- contactless/NFC acceptance;
- Verve contactless acceptance;
- Visa/Mastercard/other supported schemes;
- card-not-present capability if included in our contract.

Wave treats **card acceptance** and **contactless/NFC** as separate capabilities. Contactless will not be activated merely because card acceptance is approved.

### 5. Transaction execution contract
For the approved SmartPOS SDK/API flow, please provide:
- sale/purchase request contract;
- provider transaction reference fields;
- success/decline/pending response semantics;
- reversal rules;
- timeout handling;
- duplicate/idempotency behavior;
- transaction requery/inquiry endpoint or SDK call;
- retry policy;
- offline/stand-in behavior if supported;
- test PAN/test-card or simulator guidance supplied through the approved UAT program.

Wave will only mark a payment `SUCCEEDED` after provider-confirmed success. An authorization/pending state must not produce a final receipt.

### 6. Provider events / webhooks
Please provide the exact webhook/event contract:
- event types;
- endpoint registration process;
- signature/authentication headers;
- canonical payload/signing rules;
- signing key or certificate rotation;
- timestamp/replay protection;
- retry schedule;
- duplicate-event guarantees;
- transaction completed/reversed/settled event semantics.

Wave already has a fail-closed verification boundary and will reject provider events unless signature verification is explicitly configured.

### 7. Settlement and reconciliation
Please provide:
- settlement account onboarding process;
- supported Nigerian settlement-bank accounts;
- settlement frequency and cut-off rules;
- gross, fee, tax, and net fields;
- settlement batch/reference identifiers;
- transaction-to-settlement mapping;
- chargeback/reversal effects on settlement;
- settlement inquiry API/SDK;
- reporting/OneView access if applicable;
- UAT settlement simulation method.

### 8. QR acceptance
Please confirm whether Wave's persistent merchant QR should remain Wave-resolved and then route into Interswitch settlement, or whether an Interswitch QR product must be used for acquiring.

If an Interswitch QR product is required, please provide:
- merchant QR provisioning contract;
- QR payload format;
- dynamic/static QR rules;
- transaction status/requery contract;
- webhook/reversal/settlement behavior.

### 9. Wave-branded contactless card program
Wave wants a legitimate issuer-backed Wave Transakt physical contactless card, not a generic NFC tag.

Please provide the appropriate Card 360 / Fintech Card Issuance onboarding path and clarify:
- debit vs prepaid options available to Wave;
- sponsoring issuer/BIN arrangement;
- scheme availability (including Verve if applicable);
- KYC requirements per cardholder;
- card creation/activation/block/unblock lifecycle APIs;
- PAN/token handling and PCI scope;
- card personalization and manufacturing;
- artwork/branding approval process;
- contactless certification requirements;
- pilot minimum order quantity (Wave would like to begin with approximately five cards if permitted);
- UAT/test card availability;
- card-to-Wave-wallet/account mapping;
- settlement/funding model.

## First controlled pilot requested

Wave's preferred first pilot is:
1. one approved SmartPOS terminal;
2. one Wave merchant in UAT;
3. one real MID/TID pair assigned by Interswitch;
4. QR enabled if contractually supported;
5. card enabled only after provider grant;
6. contactless enabled only after separate provider grant;
7. provider transaction/requery/reversal verified;
8. provider settlement/reconciliation verified;
9. then a small Wave-branded contactless card pilot if card-issuing approval permits it.

## Provider response mapping into Wave

| Provider response | Wave integration point |
| --- | --- |
| Merchant onboarding approval / merchant code / MID | `MerchantAcquiringProvider` + merchant provider-link state |
| TID / physical terminal assignment | terminal provisioning orchestrator |
| Card grant | terminal `supportsCard` capability |
| Contactless grant | terminal `supportsNfc` capability |
| Transaction result/requery | `MerchantAcquiringGateway` transaction inquiry/execution adapter |
| Signed webhook/event | provider-event verification boundary |
| Settlement batch / inquiry | settlement + reconciliation services |
| Card issuing credentials/program | separate card-issuing adapter; never the POS acquiring secret |

## Security constraints

- No Interswitch backend OAuth/client secret in Android, iOS, or POS APKs.
- No locally invented MID/TID/provider-terminal ID.
- No local switch that enables card/contactless acceptance.
- No final receipt until provider-confirmed `SUCCEEDED`.
- No settlement marked complete without provider confirmation.
- No trusting unsigned/unverified provider events.
- No generic NFC cards used as substitutes for payment cards.

## Official references checked

- SmartPOS SDK: https://docs.interswitchgroup.com/docs/smartpos-sdk
- Smart POS product: https://interswitchgroup.com/products/smart-pos/
- Card 360 API: https://docs.interswitchgroup.com/v1.1/docs/card-management-service-apis
- Interswitch support / sales / partnership: https://interswitchgroup.com/support/
- Partner application: https://partners.interswitchgroup.com/English/register_email.aspx

## Ready-to-send request

Subject: **Wave Transakt – SmartPOS UAT, Merchant Acquiring and Contactless/Card-Issuing Onboarding**

Wave Transakt is integrating Interswitch as the acquiring/provider layer behind our Wave Business merchant and Wave POS products. Our merchant/POS architecture, secure terminal pairing, provider-link state machine, transaction/reconciliation models and fail-closed provider gateway are already implemented.

We are requesting technical and commercial onboarding for a controlled UAT pilot. Please provide the approved merchant onboarding flow, a test merchant/MID, a test SmartPOS terminal and TID, supported device model/SDK version, the production-safe credential model, card/contactless enablement process, transaction/requery/reversal contract, webhook verification specification, settlement/reconciliation integration, and the appropriate Card 360/Fintech Card Issuance route for a future Wave-branded contactless card pilot.

We would prefer to begin with one SmartPOS terminal and, if permitted by the issuing program, approximately five Wave-branded contactless test/pilot cards.

Please route us to the appropriate SmartPOS/acquiring technical integration and card-issuing teams and advise the required commercial/KYB documentation for Wave Transakt.