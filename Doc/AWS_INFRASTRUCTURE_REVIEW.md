# Complete AWS Infrastructure & Architecture Review

**Stack Name:** `cricscore-pro-web`
**Primary Domain:** `https://cricleague.nrkmart.in`
**API Endpoint:** `https://cricleagueapi.nrkmart.in`
**Region:** `us-east-1`
**Cost Model:** 100% AWS Free Tier / On-Demand ($0.00 base cost when idle)

---

## 1. Amazon S3 (Web UI Static Hosting)

### **Resource Details**
* **Bucket Name**: `cricscore-pro-web-112232725342-us-east-1`
* **Access Control**: **Private S3 Bucket** with `PublicAccessBlockConfiguration` blocking all public ACLs and bucket policies.
* **Access Delegation**: CloudFront Origin Access Control (OAC) `CricScoreOAC` signs S3 requests via SigV4.
* **Bucket Policy**:
  ```json
  {
    "Sid": "AllowCloudFrontServicePrincipalReadOnly",
    "Effect": "Allow",
    "Principal": { "Service": "cloudfront.amazonaws.com" },
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::cricscore-pro-web-112232725342-us-east-1/*",
    "Condition": {
      "StringEquals": {
        "AWS:SourceArn": "arn:aws:cloudfront::112232725342:distribution/E2FADRQRZIIFJQ"
      }
    }
  }
  ```

### **Deployment Command**
```powershell
aws s3 sync D:/CricketScorer-new/web/dist/ s3://cricscore-pro-web-112232725342-us-east-1/ --delete
```

---

## 2. Amazon CloudFront (CDN Content Delivery Network)

### **Resource Details**
* **Distribution ID**: `E2FADRQRZIIFJQ`
* **Domain Name**: `cricleague.nrkmart.in` (CNAME target: `d3...cloudfront.net`)
* **ACM Certificate**: `arn:aws:acm:us-east-1:112232725342:certificate/266fd1fb-e2ce-4529-bfad-4b753b959fc0` (TLS 1.2 SNI)
* **Single Page Application (SPA) Routing**:
  - Configured with `CustomErrorResponses`:
    - HTTP `403` -> Response Page `/index.html` with HTTP `200 OK`
    - HTTP `404` -> Response Page `/index.html` with HTTP `200 OK`
* **CloudFront Edge Function**:
  - `RedirectCloudFrontDomain`: Automatically 301 redirects any direct requests to `*.cloudfront.net` hostnames to the primary custom domain `https://cricleague.nrkmart.in`.

### **Cache Invalidation Command**
```powershell
aws cloudfront create-invalidation --distribution-id E2FADRQRZIIFJQ --paths "/*"
```

---

## 3. Amazon DynamoDB (Single-Table Design Database)

### **Resource Details**
* **Table Name**: `CricMatches`
* **Billing Mode**: `PAY_PER_REQUEST` (On-Demand = $0 when idle)
* **Primary Key Schema**:
  - Partition Key (`HASH`): `matchId` (`S` string)

### **Entity Partition Types Stored in `CricMatches`**
| Entity Type | Partition Key Format (`matchId`) | Payload Contents | TTL Cleanup |
| :--- | :--- | :--- | :--- |
| **Match Snapshot** | `match_<uuid>` | Full `Match` object (`ballHistory`, totals, team rosters, stats). | None (Persistent) |
| **User Profile** | `usr_<email_hash>` | Hashed password (bcrypt), `userId`, `email`, `name`, `createdAt`. | None (Persistent) |
| **Spectator Share Token** | `st_<token>` | `matchId`, `ownerId`, `expiresAtMillis`. | Managed in application |
| **Auth Rate Limiter** | `rl_<route>_<ip>` | Request counter, window timestamp. | Auto TTL |
| **Audit Logs** | `audit_<timestamp>` | Security audit event, actor email, action type, IP. | `ttlSeconds` (30 days) |
| **Error Logs** | `err_<timestamp>` | Client/server stack trace, status code, endpoint. | `ttlSeconds` (30 days) |

---

## 4. AWS Lambda Backend (`CricScoreApiLambda`)

### **Resource Details**
* **Function Name**: `CricScoreApiLambda`
* **Runtime**: Node.js 22.x (`nodejs22.x` on `x86_64`)
* **Timeout**: 10 seconds
* **Memory**: 256 MB
* **Handler File**: `aws/lambda/index.mjs`
* **Security & Secret Storage**: Uses AWS Secrets Manager (`JwtSigningSecret`) for 64-character auto-generated JWT signing keys.

### **Lambda Environment Variables**
```yaml
TABLE_NAME: CricMatches
JWT_SECRET_ARN: arn:aws:secretsmanager:us-east-1:112232725342:secret:cricscore-jwt-secret-...
ENFORCE_ANDROID_MATCH_WRITES: "false"  # Updated for Option B (Web + Android cloud writes)
ENFORCE_STRICT_MATCH_REVISION: "true"   # Strictly enforces 409 STALE_REVISION on concurrent edits
ENABLE_AUTH_RATE_LIMIT: "true"
AUTH_RATE_LIMIT_MAX_REQUESTS: "20"
AUTH_RATE_LIMIT_WINDOW_SECONDS: "60"
MATCH_WRITE_MAX_BODY_BYTES: "262144"    # 256 KB max payload size
MATCH_WRITE_MAX_BALL_EVENTS: "3000"
MATCH_WRITE_MAX_WICKET_EVENTS: "400"
```

### **API Gateway HTTP API v2 Mapping**
- Endpoint: `https://cricleagueapi.nrkmart.in`
- CORS Whitelist: `https://cricleague.nrkmart.in`, CloudFront domain, local dev environments.
- Allowed Headers: `Content-Type`, `Authorization`, `X-Client-Platform`, `X-Guest-Mode`.

---

## 5. SAM Deployment & Commands Summary

### **SAM Infrastructure Deployment Command**
```powershell
sam deploy --no-confirm-changeset \
  --stack-name cricscore-pro-web \
  --template-file aws/template.yaml \
  --capabilities CAPABILITY_IAM \
  --resolve-s3
```

### **Complete Deployment Pipeline (Backend + Web Assets)**
```powershell
# 1. Build React Web Bundle
cd D:/CricketScorer-new/web
npm run build

# 2. Deploy AWS Infrastructure via SAM
cd D:/CricketScorer-new
sam deploy --no-confirm-changeset --stack-name cricscore-pro-web --template-file aws/template.yaml --capabilities CAPABILITY_IAM --resolve-s3

# 3. Sync Web Assets to S3 & Invalidate CloudFront
aws s3 sync D:/CricketScorer-new/web/dist/ s3://cricscore-pro-web-112232725342-us-east-1/ --delete
aws cloudfront create-invalidation --distribution-id E2FADRQRZIIFJQ --paths "/*"
```
