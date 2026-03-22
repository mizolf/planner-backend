package com.mcesnik.planner_backend.controller;

import com.mcesnik.planner_backend.DTO.CreateTripDTO;
import com.mcesnik.planner_backend.mapper.TripMapper;
import com.mcesnik.planner_backend.repository.TripRepository;
import com.mcesnik.planner_backend.responses.TripResponse;
import com.mcesnik.planner_backend.service.TripService;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/trips")
public class TripController {
    private final TripMapper tripMapper;
    private final TripService tripService;

    public TripController(TripService tripService, TripMapper tripMapper) {
        this.tripService = tripService;
        this.tripMapper = tripMapper;
    }

    @PostMapping
    @ResponseStatus(CREATED)
    public TripResponse createTrip(
            @RequestBody CreateTripDTO request
    ){
        return tripMapper.toResponse(tripService.addTrip(tripMapper.toEntity(request)));
    }
}

//Trip CRUD
//Method	Path	Body	Returns	Auth
//POST	/trips	CreateTripDTO	TripResponse	Authenticated (creator becomes OWNER)
//GET	/trips	—	List<TripResponse>	Authenticated (own trips only)
//GET	/trips/{tripId}	—	TripDetailResponse	Member
//PUT	/trips/{tripId}	UpdateTripDTO	TripResponse	Editor/Owner
//DELETE	/trips/{tripId}	—	message	Owner
