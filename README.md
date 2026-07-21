# Secure Java Swing OTP Verification

A modern, visually appealing OTP Generator built with Java Swing and Jakarta Mail. 

## Features
- Sleek glassmorphism UI with gradient backgrounds and smooth animations.
- Secure email delivery using standard SMTP.
- **UI Mock Demo Mode**: If no SMTP credentials are provided, the app falls back to a safe demo mode that simulates sending the OTP and prints the code in the UI.

## Local Development & Setup

This project requires **Jakarta Mail 2.0+** and **Angus Activation**. The required `.jar` files must be in your classpath.

### Setting up Credentials

For security, **SMTP credentials are not hardcoded**. The app expects two environment variables:
- `SMTP_EMAIL`: Your sender email address (e.g., your Gmail).
- `SMTP_PASSWORD`: Your App Password (not your main account password).

**Option 1: Using a config file (Recommended for local dev)**
1. In the root of the project, create a file named `config.properties`.
2. Add the following lines:
   ```properties
   SMTP_EMAIL=your-email@gmail.com
   SMTP_PASSWORD=your-app-password
   ```
   *Note: `config.properties` is included in `.gitignore` and will never be pushed to version control.*

**Option 2: System Environment Variables**
Export the variables in your OS terminal before running the application.

## Packaging as an Executable JAR

Since it's an Eclipse project:
1. Right-click the project in Eclipse -> **Export**
2. Select **Java -> Runnable JAR file**
3. Choose `OTPGenerator` as the launch configuration.
4. Select "Package required libraries into generated JAR" to ensure the Jakarta Mail dependencies are included.
5. Save it as `OTPGenerator.jar`.

To run the JAR from the command line:
```bash
java -jar OTPGenerator.jar
```

## GitHub Release & Secrets
When deploying or sharing the source code via GitHub Actions (if configured later), add your credentials to the repository **Settings -> Secrets and variables -> Actions** as `SMTP_EMAIL` and `SMTP_PASSWORD`.
