package com.codewithsavage.majipoa.rider;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class RiderService {

    private final RiderRepository repository;

    public RiderService(RiderRepository repository) {
        this.repository = repository;
    }

    public RiderResponse create(RiderRequest request) {
        if (repository.existsByPhone(request.getPhone())) {
            throw new DuplicateRiderException("A rider with this phone number already exists");
        }
        if (notBlank(request.getEmail()) && repository.existsByEmail(request.getEmail())) {
            throw new DuplicateRiderException("A rider with this email already exists");
        }
        if (notBlank(request.getPlateNumber()) && repository.existsByPlateNumber(request.getPlateNumber())) {
            throw new DuplicateRiderException("A rider with this plate number already exists");
        }

        Rider rider = new Rider();
        apply(rider, request);
        return RiderResponse.from(repository.save(rider));
    }

    @Transactional(readOnly = true)
    public List<RiderResponse> findAll(RiderStatus status) {
        List<Rider> riders = (status == null) ? repository.findAll() : repository.findByStatus(status);
        return riders.stream().map(RiderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RiderResponse findById(Long id) {
        return RiderResponse.from(getOrThrow(id));
    }

    public RiderResponse update(Long id, RiderRequest request) {
        Rider rider = getOrThrow(id);

        if (!request.getPhone().equals(rider.getPhone()) && repository.existsByPhone(request.getPhone())) {
            throw new DuplicateRiderException("A rider with this phone number already exists");
        }
        if (notBlank(request.getEmail())
                && !request.getEmail().equals(rider.getEmail())
                && repository.existsByEmail(request.getEmail())) {
            throw new DuplicateRiderException("A rider with this email already exists");
        }
        if (notBlank(request.getPlateNumber())
                && !request.getPlateNumber().equals(rider.getPlateNumber())
                && repository.existsByPlateNumber(request.getPlateNumber())) {
            throw new DuplicateRiderException("A rider with this plate number already exists");
        }

        apply(rider, request);
        return RiderResponse.from(repository.save(rider));
    }

    public RiderResponse updateStatus(Long id, RiderStatus status) {
        Rider rider = getOrThrow(id);
        rider.setStatus(status);
        return RiderResponse.from(repository.save(rider));
    }

    public RiderResponse updateLocation(Long id, RiderLocationRequest location) {
        Rider rider = getOrThrow(id);
        rider.setLatitude(location.getLatitude());
        rider.setLongitude(location.getLongitude());
        return RiderResponse.from(repository.save(rider));
    }

    public void delete(Long id) {
        repository.delete(getOrThrow(id));
    }

    private Rider getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new RiderNotFoundException(id));
    }

    private void apply(Rider rider, RiderRequest request) {
        rider.setFullName(request.getFullName());
        rider.setPhone(request.getPhone());
        rider.setEmail(blankToNull(request.getEmail()));
        rider.setVehicleType(request.getVehicleType());
        rider.setPlateNumber(blankToNull(request.getPlateNumber()));
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String blankToNull(String s) {
        return notBlank(s) ? s : null;
    }
}
