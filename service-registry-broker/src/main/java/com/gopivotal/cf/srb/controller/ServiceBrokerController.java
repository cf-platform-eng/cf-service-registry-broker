package com.gopivotal.cf.srb.controller;

import com.gopivotal.cf.srb.model.*;
import com.gopivotal.cf.srb.repository.RegisteredServiceRepository;
import com.gopivotal.cf.srb.repository.ServiceBindingRepository;
import com.gopivotal.cf.srb.repository.ServiceInstanceRepository;
import com.gopivotal.cf.srb.repository.ServiceRepository;
import com.gopivotal.cf.srb.service.ServiceBrokerRegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.Cloud;
import org.springframework.cloud.app.ApplicationInstanceInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ServiceBrokerController {

    private final ServiceRepository serviceRepository;
    private final ServiceInstanceRepository serviceInstanceRepository;
    private final ServiceBindingRepository serviceBindingRepository;
    private final RegisteredServiceRepository registeredServiceRepository;
    private final ServiceBrokerRegistrationService serviceBrokerRegistrationService;

    @Autowired
    public ServiceBrokerController(ServiceRepository serviceRepository,
                                   ServiceInstanceRepository serviceInstanceRepository,
                                   ServiceBindingRepository serviceBindingRepository,
                                   RegisteredServiceRepository registeredServiceRepository,
                                   ServiceBrokerRegistrationService serviceBrokerRegistrationService) {
        this.serviceRepository = serviceRepository;
        this.serviceInstanceRepository = serviceInstanceRepository;
        this.serviceBindingRepository = serviceBindingRepository;
        this.registeredServiceRepository = registeredServiceRepository;
        this.serviceBrokerRegistrationService = serviceBrokerRegistrationService;
    }

    @RequestMapping("/v2/catalog")
    public Map<String, Iterable<Service>> catalog() {
        Map<String, Iterable<Service>> wrapper = new HashMap<>();
        wrapper.put("services", serviceRepository.findAll());
        return wrapper;
    }

    @RequestMapping(value = "/v2/service_instances/{id}", method = RequestMethod.PUT)
    public ResponseEntity<String> create(@PathVariable("id") String id, @RequestBody ServiceInstance serviceInstance) {
        serviceInstance.setId(id);

        boolean exists = serviceInstanceRepository.exists(id);

        if (exists) {
            ServiceInstance existing = serviceInstanceRepository.findOne(id);
            if (existing.equals(serviceInstance)) {
                return new ResponseEntity<>("{}", HttpStatus.OK);
            } else {
                return new ResponseEntity<>("{}", HttpStatus.CONFLICT);
            }
        } else {
            serviceInstanceRepository.save(serviceInstance);
            return new ResponseEntity<>("{}", HttpStatus.CREATED);
        }
    }

    @RequestMapping(value = "/v2/service_instances/{instanceId}/service_bindings/{id}", method = RequestMethod.PUT)
    public ResponseEntity<Object> createBinding(@PathVariable("instanceId") String instanceId,
                                                @PathVariable("id") String id,
                                                @RequestBody ServiceBinding serviceBinding) {
        if (!serviceInstanceRepository.exists(instanceId)) {
            return new ResponseEntity<Object>("{\"description\":\"Service instance " + instanceId + " does not exist!\"", HttpStatus.BAD_REQUEST);
        }

        serviceBinding.setId(id);
        serviceBinding.setInstanceId(instanceId);

        boolean exists = serviceBindingRepository.exists(id);

        if (exists) {
            ServiceBinding existing = serviceBindingRepository.findOne(id);
            if (existing.equals(serviceBinding)) {
                return new ResponseEntity<Object>(wrapCredentials(existing.getCredentials()), HttpStatus.OK);
            } else {
                return new ResponseEntity<Object>("{}", HttpStatus.CONFLICT);
            }
        } else {
            Service service = serviceRepository.findOne(serviceBinding.getServiceId());
            RegisteredService registeredService;
            if ("service-registry".equals(service.getName())) {
                registeredService = new RegisteredService();
                registeredService.setUrl("http://" + serviceBrokerRegistrationService.firstRoute() + "/registry");
                registeredService.setBasicAuthUser("warreng");
                registeredService.setBasicAuthPassword("natedogg");
            } else {
                registeredService = registeredServiceRepository.findByName(service.getName());
            }

            Credentials credentials = new Credentials();
            credentials.setId(UUID.randomUUID().toString());
            credentials.setUri(registeredService.getUrl());
            credentials.setUsername(registeredService.getBasicAuthUser());
            credentials.setPassword(registeredService.getBasicAuthPassword());
            serviceBinding.setCredentials(credentials);
            serviceBindingRepository.save(serviceBinding);
            return new ResponseEntity<Object>(wrapCredentials(credentials), HttpStatus.CREATED);
        }
    }

    @RequestMapping(value = "/v2/service_instances/{instanceId}/service_bindings/{id}", method = RequestMethod.DELETE)
    public ResponseEntity<String> deleteBinding(@PathVariable("instanceId") String instanceId,
                                                @PathVariable("id") String id,
                                                @RequestParam("service_id") String serviceId,
                                                @RequestParam("plan_id") String planId) {
        boolean exists = serviceBindingRepository.exists(id);

        if (exists) {
            serviceBindingRepository.delete(id);
            return new ResponseEntity<>("{}", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("{}", HttpStatus.GONE);
        }
    }

    @RequestMapping(value = "/v2/service_instances/{id}", method = RequestMethod.DELETE)
    public ResponseEntity<String> delete(@PathVariable("id") String id,
                                         @RequestParam("service_id") String serviceId,
                                         @RequestParam("plan_id") String planId) {
        boolean exists = serviceInstanceRepository.exists(id);

        if (exists) {
            serviceInstanceRepository.delete(id);
            return new ResponseEntity<>("{}", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("{}", HttpStatus.GONE);
        }
    }

    private Map<String, Object> wrapCredentials(Credentials credentials) {
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("credentials", credentials);
        return wrapper;
    }
}
