package uz.familymens.store.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CheckoutForm {

    @NotBlank(message = "{validation.name}")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "{validation.phone}")
    @Pattern(regexp = "^[+0-9()\\-\\s]{7,20}$", message = "{validation.phone}")
    private String phone;

    @Size(max = 300)
    private String address;

    @Size(max = 500)
    private String comment;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
