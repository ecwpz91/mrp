package com.redhat.mrp.model;

public class RoverResponse {

	private Rover rover;

	public Rover getRover() {
		return rover;
	}

	public void setRover(Rover rover) {
		this.rover = rover;
	}

	@Override
	public String toString() {
		return "{rover=" + rover + "}";
	}

}
