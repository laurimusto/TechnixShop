FROM node:22-alpine
WORKDIR /app

# copy the lockfile first: npm ci is cached until dependencies change
COPY package.json package-lock.json ./
RUN npm ci

# NEXT_PUBLIC_* values are written into the JavaScript at build time, so they are build args
ARG NEXT_PUBLIC_RECAPTCHA_SITE_KEY
ARG NEXT_PUBLIC_GOOGLE_CLIENT_ID
ENV NEXT_PUBLIC_RECAPTCHA_SITE_KEY=$NEXT_PUBLIC_RECAPTCHA_SITE_KEY \
    NEXT_PUBLIC_GOOGLE_CLIENT_ID=$NEXT_PUBLIC_GOOGLE_CLIENT_ID \
    NEXT_TELEMETRY_DISABLED=1

COPY . .
RUN npm run build

ENV NODE_ENV=production
USER node
EXPOSE 3000
CMD ["npm", "start"]
