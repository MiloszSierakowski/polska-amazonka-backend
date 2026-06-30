FROM mcr.microsoft.com/playwright:v1.61.1-jammy

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY src ./src

ENV PORT=3001
ENV HEADLESS=true
ENV ENABLE_SCREENSHOTS=false

EXPOSE 3001

CMD ["npm", "start"]
