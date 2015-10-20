/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.ratelimit.local;

import io.gravitee.repository.ratelimit.api.RateLimitRepository;
import io.gravitee.repository.ratelimit.model.RateLimitResult;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author David BRASSELY (brasseld at gmail.com)
 */
public class LocalCacheRateLimitProvider implements RateLimitRepository {

    private Map<Serializable, RateLimit> rateLimits = new HashMap<>();

    @Override
    public RateLimitResult acquire(Serializable key, int pound, long limit, long periodTime, TimeUnit periodTimeUnit) {
        RateLimit rateLimit = rateLimits.getOrDefault(key, new RateLimit());
        RateLimitResult rateLimitResult = new RateLimitResult();

        // We prefer currentTimeMillis in place of nanoTime() because nanoTime is relatively
        // expensive call and depends on the underlying architecture.

        long now = System.currentTimeMillis();
        long lastCheck = rateLimit.getLastRequest();
        long endOfWindow = getEndOfPeriod(lastCheck, periodTime, periodTimeUnit);

        if (now >= endOfWindow) {
            rateLimit.setCounter(0);
        }

        if (rateLimit.getCounter() >= limit) {
            rateLimitResult.setExceeded(true);
        } else {
            // Update rate limiter
            rateLimitResult.setExceeded(false);
            rateLimit.setCounter(rateLimit.getCounter() + pound);
            rateLimit.setLastRequest(now);
        }

        // Set the time at which the current rate limit window resets in UTC epoch seconds.
        rateLimitResult.setResetTime(getEndOfPeriod(now, periodTime, periodTimeUnit) / 1000L);
        rateLimitResult.setRemains(limit - rateLimit.getCounter());

        rateLimits.put(key, rateLimit);

        return rateLimitResult;
    }

    public void clean() {
        rateLimits.clear();
    }

    private long getEndOfPeriod(long startingTime, long periodTime, TimeUnit periodTimeUnit) {
        Duration duration = null;

        switch (periodTimeUnit) {
            case SECONDS:
                duration = Duration.ofSeconds(periodTime);
                break;
            case MINUTES:
                duration = Duration.ofMinutes(periodTime);
                break;
            case HOURS:
                duration = Duration.ofHours(periodTime);
                break;
            case DAYS:
                duration = Duration.ofDays(periodTime);
                break;
        }

        return Instant.ofEpochMilli(startingTime).plus(duration).toEpochMilli();
    }
}