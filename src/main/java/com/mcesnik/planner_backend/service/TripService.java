package com.mcesnik.planner_backend.service;

import com.mcesnik.planner_backend.DTO.CreateTripDTO;
import com.mcesnik.planner_backend.DTO.UpdateTripDTO;
import com.mcesnik.planner_backend.mapper.TripMapper;
import com.mcesnik.planner_backend.model.User;
import com.mcesnik.planner_backend.responses.TripDetailResponse;
import com.mcesnik.planner_backend.responses.TripResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TripService {

    private final TripMapper tripMapper;

    public TripService(TripMapper tripMapper) {
        this.tripMapper = tripMapper;
    }

//    public TripResponse createTrip(CreateTripDTO dto, User currentUser)
//        // - mapper.toEntity(dto), postavi status=PLANNING
//        // - save trip
//        // - kreiraj UserTrip s role=OWNER za currentUser
//        // - vrati TripResponse
//    }
//
//    public List<TripResponse> getTripsForUser(User currentUser)
//    // - userTripRepository.findByUserId(currentUser.getId())
//    // - mapiraj svaki UserTrip → trip → TripResponse
//
//    public TripDetailResponse getTripDetail(Long tripId, User currentUser)
//    // - validateMembership(tripId, currentUser)
//    // - dohvati trip, dane (sortirane), aktivnosti za svaki dan, članove
//    // - mapper.toDetailResponse(...)
//
//    public TripResponse updateTrip(Long tripId, UpdateTripDTO dto, User currentUser)
//    // - validateEditorOrOwner(tripId, currentUser)
//    // - dohvati trip, primijeni non-null polja iz DTO
//    // - save, vrati TripResponse
//
//    public void deleteTrip(Long tripId, User currentUser)
//    // - validateOwner(tripId, currentUser)
//    // - delete trip (cascade briše dane, aktivnosti, članove)
}
