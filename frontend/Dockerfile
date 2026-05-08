FROM node:20-alpine

WORKDIR /app

COPY package*.json ./
RUN npm install --include=dev

COPY . .

ARG REACT_APP_API_BASE
ARG REACT_APP_REALTIME_TRANSPORT=websocket
ENV REACT_APP_API_BASE=$REACT_APP_API_BASE
ENV REACT_APP_REALTIME_TRANSPORT=$REACT_APP_REALTIME_TRANSPORT

RUN npm run build

ENV NODE_ENV=production
CMD ["npm", "run", "start:prod"]
