# Crypto Intelligence Platform

A cryptocurrency market intelligence and trading simulation platform. Track real-time prices, execute simulated trades, monitor portfolio performance, and analyze market trends.

**This is a simulation environment** - no real financial transactions are executed.

## Tech Stack

- **Frontend**: HTML5, CSS3, vanilla JavaScript, Chart.js
- **Backend**: Java 17, Spring Boot 3.2, Spring Security (JWT), Spring Data JPA
- **Database**: PostgreSQL (production) / H2 (development)
- **Deployment**: Docker, Railway

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.9+
- PostgreSQL 14+ (for production; H2 used by default in dev)

### Development Setup

```bash
cd backend
mvn spring-boot:run
```

The app starts on `http://localhost:8080` with an in-memory H2 database.

Default admin credentials (seeded by migration):
- Email: `admin@cryptointel.com`
- Password: `Admin123!`

### Environment Variables

Copy `.env.example` and configure:

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_URL` | JDBC database URL | `jdbc:h2:mem:cryptointel` |
| `DB_USERNAME` | Database username | `sa` |
| `DB_PASSWORD` | Database password | (empty) |
| `JWT_SECRET` | JWT signing key (min 32 chars) | dev default |
| `PORT` | Server port | `8080` |
| `SPRING_PROFILES_ACTIVE` | Active profile | `dev` |

### Production (PostgreSQL)

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:postgresql://localhost:5432/cryptointel
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
export JWT_SECRET=your-secure-256-bit-secret-key-here
cd backend
mvn spring-boot:run
```

### Docker

```bash
docker build -t crypto-intel .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host:5432/cryptointel \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=secret \
  -e JWT_SECRET=your-secret-key \
  -e SPRING_PROFILES_ACTIVE=prod \
  crypto-intel
```

### Railway Deployment

1. Connect repository to Railway
2. Add PostgreSQL service
3. Set environment variables in Railway dashboard
4. Deploy triggers automatically on push

## API Endpoints

### Authentication

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/auth/register` | Register new user | No |
| POST | `/api/auth/login` | Authenticate user | No |
| GET | `/api/auth/validate` | Validate JWT token | Yes |

### Market Data

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/market/top20` | Top 20 cryptocurrencies | Yes |
| GET | `/api/market/history/{coinId}?days=N` | Price history | Yes |

### Trading

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/trade` | Execute simulated trade | Yes |
| GET | `/api/portfolio` | Get portfolio summary | Yes |
| GET | `/api/transactions` | Get transaction history | Yes |

**POST /api/trade** request body:

```json
{
  "coinId": "bitcoin",
  "action": "BUY",
  "amountUsd": 500.00
}
```

- `coinId` — CoinGecko coin identifier (e.g. `bitcoin`, `ethereum`)
- `action` — `BUY` or `SELL` (uppercase)
- `amountUsd` — positive number, USD amount to trade

### Admin (ADMIN role required)

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/admin/users` | List all users | Admin |
| GET | `/api/admin/transactions` | List all transactions | Admin |

### Health

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/health` | Health check | No |

## Project Structure

```
├── backend/
│   └── src/main/
│       ├── java/com/cryptointel/
│       │   ├── config/          # Security, web, error handling
│       │   ├── controllers/     # REST API endpoints
│       │   ├── models/          # JPA entities
│       │   ├── repositories/    # Spring Data JPA repos
│       │   ├── security/        # JWT filter & provider
│       │   └── services/        # Business logic
│       └── resources/
│           ├── static/          # Frontend (HTML, CSS, JS)
│           ├── db/migration/    # Flyway migrations
│           └── application.yml  # Configuration
├── database/                    # PostgreSQL-specific schema
├── Dockerfile
├── railway.toml
└── .env.example
```

## Features

- JWT authentication with BCrypt password hashing
- Real-time market data from CoinGecko API (30s cache)
- Interactive Chart.js price charts (1H, 24H, 7D, 30D)
- Simulated buy/sell trading with portfolio tracking
- Admin panel for user and transaction oversight
- Dark theme UI with responsive design
- Database persistence with Flyway migrations
