package com.mcesnik.planner_backend.service.ai;

import com.mcesnik.planner_backend.DTO.ai.GeneratedActivity;
import com.mcesnik.planner_backend.DTO.ai.GeneratedDay;
import com.mcesnik.planner_backend.DTO.ai.GeneratedItinerary;
import com.mcesnik.planner_backend.exception.AIGenerationException;
import com.mcesnik.planner_backend.exception.ResourceNotFoundException;
import com.mcesnik.planner_backend.exception.TripConflictException;
import com.mcesnik.planner_backend.model.Activity;
import com.mcesnik.planner_backend.model.Enums.ActivityCategory;
import com.mcesnik.planner_backend.model.Trip;
import com.mcesnik.planner_backend.model.TripDay;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.repository.TripDayRepository;
import com.mcesnik.planner_backend.repository.TripRepository;
import com.mcesnik.planner_backend.responses.TripDetailResponse;
import com.mcesnik.planner_backend.service.GeminiClient;
import com.mcesnik.planner_backend.service.GeocodingService;
import com.mcesnik.planner_backend.service.TripAuthorizationService;
import com.mcesnik.planner_backend.service.TripService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class ItineraryGenerationService {

    private final TripAuthorizationService tripAuthorizationService;
    private final TripRepository tripRepository;
    private final TripDayRepository tripDayRepository;
    private final GeminiClient geminiClient;
    private final GeocodingService geocodingService;
    private final TripService tripService;

    public ItineraryGenerationService(TripAuthorizationService tripAuthorizationService,
                                      TripRepository tripRepository,
                                      TripDayRepository tripDayRepository,
                                      GeminiClient geminiClient,
                                      GeocodingService geocodingService,
                                      TripService tripService) {
        this.tripAuthorizationService = tripAuthorizationService;
        this.tripRepository = tripRepository;
        this.tripDayRepository = tripDayRepository;
        this.geminiClient = geminiClient;
        this.geocodingService = geocodingService;
        this.tripService = tripService;
    }

    @Transactional
    public TripDetailResponse generateItinerary(Long tripId, User currentUser) {
        tripAuthorizationService.validateEditorOrOwner(tripId, currentUser);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        var numberOfDays = ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;

        if (!trip.getDays().isEmpty()) {
            throw new TripConflictException(
                    TripConflictException.Code.ITINERARY_NOT_EMPTY,
                    "You already have added days for this trip");
        }

        Coordinates center;
        if(trip.getLatitude() != null && trip.getLongitude() != null){
            center = new Coordinates(trip.getLatitude(), trip.getLongitude());
        } else {
            center = geocodingService.geocode(trip.getDestination(), null, null).orElse(null);
        }

        GeneratedItinerary generatedItinerary = geminiClient.generate(trip.getDestination(), (int) numberOfDays, trip.getBudget(), trip.getInterests());
        if (generatedItinerary == null || generatedItinerary.days() == null) {
            throw new AIGenerationException("Gemini returned no itinerary days");
        }

        List<GeneratedDay> generatedDays = generatedItinerary.days();
        List<TripDay> days = new ArrayList<>();

        for (int i = 0; i < generatedDays.size() && i < numberOfDays; i++) {
            GeneratedDay generatedDay = generatedDays.get(i);

            TripDay day = TripDay.builder()
                    .trip(trip)
                    .dayNumber(i + 1)
                    .date(trip.getStartDate().plusDays(i))
                    .title(generatedDay.title())
                    .build();

            List<Activity> activities = new ArrayList<>();
            if (generatedDay.activities() != null) {
                for (GeneratedActivity generatedActivity : generatedDay.activities()) {
                    Coordinates coordinates = geocodingService
                            .geocodeActivity(generatedActivity.location(), trip.getDestination(), center)
                            .orElse(null);

                    Double latitude = coordinates != null ? coordinates.latitude() : null;
                    Double longitude = coordinates != null ? coordinates.longitude() : null;

                    Activity activity = Activity.builder()
                            .tripDay(day)
                            .name(generatedActivity.name())
                            .description(generatedActivity.description())
                            .location(generatedActivity.location())
                            .latitude(latitude)
                            .longitude(longitude)
                            .startTime(parseTime(generatedActivity.startTime()))
                            .endTime(parseTime(generatedActivity.endTime()))
                            .category(parseCategory(generatedActivity.category()))
                            .cost(generatedActivity.cost())
                            .build();

                    activities.add(activity);
                }
            }

            day.setActivities(activities);
            days.add(day);
        }

        tripDayRepository.saveAll(days);

        return tripService.getTripDetail(tripId, currentUser);
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

    private LocalTime parseTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim(), TIME_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private ActivityCategory parseCategory(String value) {
        if (value == null) {
            return ActivityCategory.OTHER;
        }
        try {
            return ActivityCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ActivityCategory.OTHER;
        }
    }
}
